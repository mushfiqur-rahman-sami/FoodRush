package com.foodrush.app.call;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.foodrush.app.FoodRushApp;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.model.ChatMessage;
import com.foodrush.app.ui.CallActivity;
import com.foodrush.app.ui.ChatActivity;
import com.foodrush.app.util.NotificationHelper;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Keeps an ear open for new chat messages and incoming calls while a foreground service runs.
 * Firebase's free plan has no server to push these to a closed app, so the customer's tracking
 * service and the rider's location service host this watcher during active deliveries.
 */
public final class CommsWatcher {

    private static final String TAG = "CommsWatcher";
    private static final long RING_WINDOW_MS = 45_000;

    private final Context app;
    private final String myUid;
    private final Map<String, ListenerRegistration> chats = new HashMap<>();
    private final Set<String> ringing = new HashSet<>();
    private ListenerRegistration calls;

    public CommsWatcher(Context context, String myUid) {
        this.app = context.getApplicationContext();
        this.myUid = myUid;
    }

    public void start() {
        if (calls != null || !Cloud.isOn(app)) {
            return;
        }
        calls = Cloud.db().collection(Cloud.CALLS).whereEqualTo("calleeUid", myUid).addSnapshotListener((result, e) -> {
            if (e != null) {
                Log.w(TAG, "call listener failed", e);
                return;
            }
            if (result == null) {
                return;
            }
            for (DocumentChange change : result.getDocumentChanges()) {
                QueryDocumentSnapshot doc = change.getDocument();
                String state = doc.getString("state");
                Long createdAt = doc.getLong("createdAt");
                boolean fresh = createdAt != null && System.currentTimeMillis() - createdAt < RING_WINDOW_MS;
                if (change.getType() != DocumentChange.Type.REMOVED && CallSession.STATE_RINGING.equals(state) && fresh) {
                    if (ringing.add(doc.getId())) {
                        ring(doc.getId(), doc.getString("callerName"), doc.getString("orderId"));
                    }
                } else if (ringing.remove(doc.getId())) {
                    NotificationHelper.cancel(app, NotificationHelper.CALL_ID);
                }
            }
        });
    }

    private void ring(String callId, String callerName, String orderId) {
        Intent screen = new Intent(app, CallActivity.class)
                .putExtra(CallActivity.EXTRA_CALL_ID, callId)
                .putExtra(CallActivity.EXTRA_ORDER_ID, orderId)
                .putExtra(CallActivity.EXTRA_PEER_NAME, callerName)
                .putExtra(CallActivity.EXTRA_OUTGOING, false)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (FoodRushApp.isInForeground()) {
            app.startActivity(screen);
        } else {
            NotificationHelper.incomingCall(app, screen, callerName == null ? "" : callerName);
        }
    }

    /** Listens to the chats of exactly these orders. */
    public void watchChats(Collection<String> orderIds) {
        if (!Cloud.isOn(app)) {
            return;
        }
        Iterator<Map.Entry<String, ListenerRegistration>> it = chats.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = it.next();
            if (!orderIds.contains(entry.getKey())) {
                entry.getValue().remove();
                it.remove();
            }
        }
        for (String orderId : orderIds) {
            if (chats.containsKey(orderId)) {
                continue;
            }
            boolean[] first = {true};
            chats.put(orderId, Cloud.db().collection(Cloud.ORDERS).document(orderId).collection(Cloud.MESSAGES)
                    .addSnapshotListener((result, e) -> {
                        if (result == null) {
                            return;
                        }
                        // The first snapshot is the history; only later additions are new.
                        if (first[0]) {
                            first[0] = false;
                            return;
                        }
                        for (DocumentChange change : result.getDocumentChanges()) {
                            if (change.getType() != DocumentChange.Type.ADDED) {
                                continue;
                            }
                            ChatMessage message = ChatMessage.from(change.getDocument().getId(), change.getDocument().getData());
                            if (!myUid.equals(message.fromUid) && !orderId.equals(ChatActivity.visibleOrderId())) {
                                NotificationHelper.chatMessage(app, orderId, message.fromName, message.text);
                            }
                        }
                    }));
        }
    }

    public void stop() {
        if (calls != null) {
            calls.remove();
            calls = null;
        }
        for (ListenerRegistration registration : chats.values()) {
            registration.remove();
        }
        chats.clear();
    }
}
