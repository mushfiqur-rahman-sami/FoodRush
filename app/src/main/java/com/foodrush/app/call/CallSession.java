package com.foodrush.app.call;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.foodrush.app.BuildConfig;
import com.foodrush.app.data.Cloud;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One free, in-app voice call over the internet with WebRTC.
 *
 * WebRTC needs the two phones to swap a little setup text (an "offer", an "answer" and network
 * candidates) before audio flows directly between them. Firestore carries that text, in calls/{id}.
 * Google's public STUN server helps each phone find its public address; a TURN relay from
 * local.properties, if set, rescues networks that block direct connections.
 */
public final class CallSession {

    public static final String STATE_RINGING = "ringing";
    public static final String STATE_ACCEPTED = "accepted";
    public static final String STATE_DECLINED = "declined";
    public static final String STATE_ENDED = "ended";
    public static final String STATE_MISSED = "missed";

    private static final String TAG = "CallSession";

    public interface Listener {
        /** The other phone answered; audio is being set up. */
        void onAnswered();

        void onConnected();

        /** The connection dropped (often a network switch) and is being restored. */
        void onReconnecting();

        void onReconnected();

        /** The call ended for {@code reason}: one of the STATE_ constants, or "failed". */
        void onEnded(String reason);
    }

    private static final long RESTART_DELAY_MILLIS = 3_000;
    private static final long CALLEE_GIVE_UP_MILLIS = 25_000;
    private static final int MAX_RESTARTS = 3;

    private final Context app;
    private final String callId;
    private final String myUid;
    private final boolean caller;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<IceCandidate> pendingRemote = new ArrayList<>();

    private PeerConnectionFactory factory;
    private AudioDeviceModule audioModule;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private PeerConnection peer;
    private ListenerRegistration callListener;
    private ListenerRegistration candidateListener;
    private String lastOffer;
    private String lastAnswer;
    private boolean finished;
    private boolean connectedOnce;
    private boolean down;
    private int restarts;
    /** A dropped connection gets a few seconds to come back by itself before the caller restarts it. */
    private final Runnable restartIfStillDown = this::restartIfStillDown;
    /** The callee can't restart; if the caller's restart doesn't bring the call back, give up. */
    private final Runnable failIfStillDown = this::failIfStillDown;

    public CallSession(Context context, String callId, String myUid, boolean caller, Listener listener) {
        this.app = context.getApplicationContext();
        this.callId = callId;
        this.myUid = myUid;
        this.caller = caller;
        this.listener = listener;
    }

    private DocumentReference doc() {
        return Cloud.db().collection(Cloud.CALLS).document(callId);
    }

    /** Creates the call document that rings the other phone. Returns its id. */
    public static String ring(String callerUid, String callerName, String calleeUid, String calleeName, String orderId) {
        DocumentReference ref = Cloud.db().collection(Cloud.CALLS).document();
        Map<String, Object> call = new HashMap<>();
        call.put("callerUid", callerUid);
        call.put("callerName", callerName);
        call.put("calleeUid", calleeUid);
        call.put("calleeName", calleeName);
        call.put("orderId", orderId);
        call.put("state", STATE_RINGING);
        call.put("createdAt", System.currentTimeMillis());
        ref.set(call).addOnFailureListener(e -> Log.w(TAG, "ring failed", e));
        return ref.getId();
    }

    public static void setState(String callId, String state) {
        Cloud.db().collection(Cloud.CALLS).document(callId).update("state", state)
                .addOnFailureListener(e -> Log.w(TAG, "state update failed", e));
    }

    /** Caller: starts right away. Callee: call after the person taps Answer. */
    public void start() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(app)
                .createInitializationOptions());
        audioModule = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory();
        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("foodrush-audio", audioSource);

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(
                iceServers(BuildConfig.TURN_URL, BuildConfig.TURN_USERNAME, BuildConfig.TURN_PASSWORD));
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        // Keep finding network paths for the whole call, so it survives a switch between Wi-Fi and data.
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        peer = factory.createPeerConnection(config, new PeerObserver());
        if (peer == null) {
            finish("failed");
            return;
        }
        peer.addTrack(audioTrack, Collections.singletonList("foodrush-stream"));
        listenForCandidates();
        if (caller) {
            sendOffer(false);
        }
        callListener = doc().addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) {
                if (snapshot != null && !snapshot.exists()) {
                    finish(STATE_ENDED);
                }
                return;
            }
            String state = snapshot.getString("state");
            if (STATE_DECLINED.equals(state) || STATE_ENDED.equals(state) || STATE_MISSED.equals(state)) {
                finish(state);
                return;
            }
            // An offer or answer can arrive more than once: a reconnect sends a fresh pair.
            if (caller && snapshot.get("answer") instanceof Map) {
                SessionDescription answer = read(snapshot.get("answer"));
                if (!answer.description.equals(lastAnswer)) {
                    if (lastAnswer == null) {
                        listener.onAnswered();
                    }
                    lastAnswer = answer.description;
                    setRemote(answer, null);
                }
            } else if (!caller && snapshot.get("offer") instanceof Map) {
                SessionDescription offer = read(snapshot.get("offer"));
                if (!offer.description.equals(lastOffer)) {
                    lastOffer = offer.description;
                    setRemote(offer, this::sendAnswer);
                }
            }
        });
    }

    /** The caller makes every offer, including the fresh one that restarts a dropped connection. */
    private void sendOffer(boolean iceRestart) {
        MediaConstraints constraints = audioConstraints();
        if (iceRestart) {
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
        }
        peer.createOffer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                peer.setLocalDescription(new SdpAdapter(), offer);
                doc().update("offer", describe(offer));
            }
        }, constraints);
    }

    private void sendAnswer() {
        peer.createAnswer(new SdpAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription answer) {
                peer.setLocalDescription(new SdpAdapter(), answer);
                Map<String, Object> update = new HashMap<>();
                update.put("answer", describe(answer));
                update.put("state", STATE_ACCEPTED);
                doc().update(update);
            }
        }, audioConstraints());
    }

    private void restartIfStillDown() {
        if (down && caller) {
            restartIce();
        }
    }

    private void failIfStillDown() {
        if (down) {
            finish("failed");
        }
    }

    private void restartIce() {
        if (finished || peer == null) {
            return;
        }
        if (restarts >= MAX_RESTARTS) {
            finish("failed");
            return;
        }
        restarts++;
        Log.i(TAG, "Restarting ICE, attempt " + restarts);
        peer.restartIce();
        sendOffer(true);
    }

    /**
     * STUN servers find each phone's public address; TURN servers relay the audio when the two phones
     * can't reach each other directly, which is common on mobile data. {@code turnUrls} is a
     * comma-separated list; "stun:" entries need no login, "turn:" and "turns:" ones use it.
     */
    static List<PeerConnection.IceServer> iceServers(String turnUrls, String username, String password) {
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        for (String stun : urlsOfKind(turnUrls, false)) {
            servers.add(PeerConnection.IceServer.builder(stun).createIceServer());
        }
        List<String> relays = urlsOfKind(turnUrls, true);
        if (!relays.isEmpty() && !username.isEmpty() && !password.isEmpty()) {
            servers.add(PeerConnection.IceServer.builder(relays)
                    .setUsername(username)
                    .setPassword(password)
                    .createIceServer());
        } else if (!relays.isEmpty()) {
            Log.w(TAG, "TURN relay set without a username or password; long-distance calls may not connect");
        }
        return servers;
    }

    /** The "turn:"/"turns:" entries (relay true) or the "stun:" ones from a comma-separated list. */
    static List<String> urlsOfKind(String urls, boolean relay) {
        List<String> out = new ArrayList<>();
        for (String url : urls.split(",")) {
            String clean = url.trim();
            boolean isRelay = clean.startsWith("turn:") || clean.startsWith("turns:");
            if (relay ? isRelay : clean.startsWith("stun:")) {
                out.add(clean);
            }
        }
        return out;
    }

    /** True when a relay (TURN) server is configured with its login. */
    public static boolean hasRelay() {
        return BuildConfig.TURN_URL.contains("turn") && !BuildConfig.TURN_USERNAME.isEmpty()
                && !BuildConfig.TURN_PASSWORD.isEmpty();
    }

    private static MediaConstraints audioConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        return constraints;
    }

    private static Map<String, Object> describe(SessionDescription description) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", description.type.canonicalForm());
        m.put("sdp", description.description);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static SessionDescription read(Object value) {
        Map<String, Object> m = (Map<String, Object>) value;
        return new SessionDescription(SessionDescription.Type.fromCanonicalForm(String.valueOf(m.get("type"))),
                String.valueOf(m.get("sdp")));
    }

    /** Applies the other phone's offer or answer, then runs {@code then} (the answer, for the callee). */
    private void setRemote(SessionDescription description, Runnable then) {
        peer.setRemoteDescription(new SdpAdapter() {
            @Override
            public void onSetSuccess() {
                synchronized (pendingRemote) {
                    for (IceCandidate candidate : pendingRemote) {
                        peer.addIceCandidate(candidate);
                    }
                    pendingRemote.clear();
                }
                if (then != null) {
                    then.run();
                }
            }
        }, description);
    }

    private void listenForCandidates() {
        candidateListener = doc().collection("candidates").addSnapshotListener((result, e) -> {
            if (result == null || peer == null) {
                return;
            }
            for (DocumentChange change : result.getDocumentChanges()) {
                if (change.getType() != DocumentChange.Type.ADDED) {
                    continue;
                }
                QueryDocumentSnapshot d = change.getDocument();
                if (myUid.equals(d.getString("from"))) {
                    continue;
                }
                Long index = d.getLong("sdpMLineIndex");
                IceCandidate candidate = new IceCandidate(d.getString("sdpMid"), index == null ? 0 : index.intValue(),
                        d.getString("sdp"));
                synchronized (pendingRemote) {
                    if (peer.getRemoteDescription() == null) {
                        pendingRemote.add(candidate);
                    } else {
                        peer.addIceCandidate(candidate);
                    }
                }
            }
        });
    }

    public void setMuted(boolean muted) {
        if (audioTrack != null) {
            audioTrack.setEnabled(!muted);
        }
    }

    /** Ends the call for both sides, then tidies the call record away once the other phone has seen it. */
    public void hangUp(String reason) {
        setState(callId, reason);
        finish(reason);
        removeLater(callId);
    }

    public static void removeLater(String callId) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> Cloud.db().collection(Cloud.CALLS).document(callId)
                .delete().addOnFailureListener(e -> Log.w(TAG, "call cleanup failed", e)), 5000);
    }

    private void finish(String reason) {
        main.post(() -> {
            if (finished) {
                return;
            }
            finished = true;
            main.removeCallbacks(restartIfStillDown);
            main.removeCallbacks(failIfStillDown);
            if (callListener != null) {
                callListener.remove();
            }
            if (candidateListener != null) {
                candidateListener.remove();
            }
            if (peer != null) {
                peer.close();
                peer.dispose();
                peer = null;
            }
            if (audioSource != null) {
                audioSource.dispose();
            }
            if (factory != null) {
                factory.dispose();
            }
            if (audioModule != null) {
                audioModule.release();
            }
            listener.onEnded(reason);
        });
    }

    /** Runs on the main thread. */
    private void onIceState(PeerConnection.IceConnectionState state) {
        if (finished) {
            return;
        }
        switch (state) {
            case CONNECTED:
            case COMPLETED:
                down = false;
                main.removeCallbacks(restartIfStillDown);
                main.removeCallbacks(failIfStillDown);
                if (!connectedOnce) {
                    connectedOnce = true;
                    listener.onConnected();
                } else {
                    listener.onReconnected();
                }
                break;
            case DISCONNECTED:
                down = true;
                if (connectedOnce) {
                    listener.onReconnecting();
                }
                if (caller) {
                    main.postDelayed(restartIfStillDown, RESTART_DELAY_MILLIS);
                } else {
                    main.postDelayed(failIfStillDown, CALLEE_GIVE_UP_MILLIS);
                }
                break;
            case FAILED:
                down = true;
                if (caller) {
                    main.removeCallbacks(restartIfStillDown);
                    restartIce();
                } else {
                    main.removeCallbacks(failIfStillDown);
                    main.postDelayed(failIfStillDown, CALLEE_GIVE_UP_MILLIS);
                }
                break;
            default:
                break;
        }
    }

    private class PeerObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(IceCandidate candidate) {
            Map<String, Object> m = new HashMap<>();
            m.put("from", myUid);
            m.put("sdpMid", candidate.sdpMid);
            m.put("sdpMLineIndex", candidate.sdpMLineIndex);
            m.put("sdp", candidate.sdp);
            doc().collection("candidates").add(m);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
            main.post(() -> onIceState(state));
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState state) {
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        }

        @Override
        public void onAddStream(MediaStream stream) {
        }

        @Override
        public void onRemoveStream(MediaStream stream) {
        }

        @Override
        public void onDataChannel(DataChannel channel) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }
    }

    /** SdpObserver with empty defaults, so each use overrides only what it needs. */
    private static class SdpAdapter implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription description) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String error) {
            Log.w(TAG, "SDP create failed: " + error);
        }

        @Override
        public void onSetFailure(String error) {
            Log.w(TAG, "SDP set failed: " + error);
        }
    }
}
