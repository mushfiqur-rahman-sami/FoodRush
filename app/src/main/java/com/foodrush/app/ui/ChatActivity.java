package com.foodrush.app.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.ChatAdapter;
import com.foodrush.app.adapter.ChipAdapter;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityChatBinding;
import com.foodrush.app.model.ChatMessage;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.util.NotificationHelper;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Customer-rider chat for one order, stored in orders/{id}/messages and updated live. It turns
 * read-only once the order is delivered or cancelled.
 */
public class ChatActivity extends BaseActivity {

    public static final String EXTRA_ORDER_ID = "order_id";

    private static volatile String visibleOrderId;

    private ActivityChatBinding binding;
    private Session session;
    private String orderId;
    private Order order;
    private ChatAdapter adapter;
    private ListenerRegistration messages;
    private OrderStore.Subscription orderSubscription;

    /** The order whose chat is on screen, so new messages for it don't also raise a notification. */
    public static String visibleOrderId() {
        return visibleOrderId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = new Session(this);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        if (orderId == null || !Cloud.isOn(this)) {
            finish();
            return;
        }

        adapter = new ChatAdapter(session.uid());
        LinearLayoutManager layout = new LinearLayoutManager(this);
        layout.setStackFromEnd(true);
        binding.chatMessages.setLayoutManager(layout);
        binding.chatMessages.setAdapter(adapter);

        List<String> quick = Arrays.asList(getResources().getStringArray(
                session.isRider() ? R.array.quick_replies_rider : R.array.quick_replies_customer));
        ChipAdapter quickAdapter = new ChipAdapter(this::send);
        quickAdapter.submit(quick, null);
        binding.chatQuick.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.chatQuick.setAdapter(quickAdapter);

        binding.chatBack.setOnClickListener(v -> finish());
        binding.chatSend.setOnClickListener(v -> sendTyped());
        binding.chatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                sendTyped();
                return true;
            }
            return false;
        });
        binding.chatCall.setOnClickListener(v -> {
            if (order != null) {
                boolean rider = session.isRider();
                CallActivity.callAbout(this, order, rider ? order.customerUid : order.riderUid,
                        rider ? order.customerName : order.riderName);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (orderId == null) {
            return;
        }
        orderSubscription = OrderStore.watch(this, orderId, this::showOrder);
        messages = Cloud.db().collection(Cloud.ORDERS).document(orderId).collection(Cloud.MESSAGES)
                .orderBy("sentAt", Query.Direction.ASCENDING)
                .addSnapshotListener((result, e) -> {
                    if (result == null) {
                        return;
                    }
                    List<ChatMessage> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : result) {
                        list.add(ChatMessage.from(doc.getId(), doc.getData()));
                    }
                    adapter.submit(list);
                    if (!list.isEmpty()) {
                        binding.chatMessages.scrollToPosition(list.size() - 1);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        visibleOrderId = orderId;
        NotificationHelper.cancel(this, ("chat" + orderId).hashCode());
    }

    @Override
    protected void onPause() {
        visibleOrderId = null;
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (messages != null) {
            messages.remove();
            messages = null;
        }
        if (orderSubscription != null) {
            orderSubscription.remove();
            orderSubscription = null;
        }
        super.onStop();
    }

    private void showOrder(Order latest) {
        order = latest;
        boolean rider = session.isRider();
        String peer = rider ? order.customerName : order.riderName;
        binding.chatTitle.setText(peer == null || peer.isEmpty() ? getString(R.string.chat_title_waiting) : peer);
        binding.chatSubtitle.setText(getString(R.string.chat_subtitle, order.shortId()));
        boolean open = order.isActive() && order.hasRealRider();
        binding.chatInputRow.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.chatQuick.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.chatCall.setVisibility(open ? View.VISIBLE : View.GONE);
        binding.chatClosed.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    private void sendTyped() {
        String text = binding.chatInput.getText().toString().trim();
        if (!text.isEmpty()) {
            send(text);
            binding.chatInput.setText("");
        }
    }

    private void send(String text) {
        if (order == null || !order.isActive()) {
            return;
        }
        ChatMessage message = new ChatMessage(null, session.uid(), session.name(), text, System.currentTimeMillis());
        Cloud.db().collection(Cloud.ORDERS).document(orderId).collection(Cloud.MESSAGES).add(message.toMap())
                .addOnFailureListener(e -> showMessage(getString(R.string.chat_failed)));
    }
}
