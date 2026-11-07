package com.foodrush.app.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.foodrush.app.R;
import com.foodrush.app.call.CallSession;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityCallBinding;
import com.foodrush.app.model.ChatMessage;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.util.NotificationHelper;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

/**
 * In-app voice call between a customer and their rider. If the internet call can't get through
 * (some mobile networks block it), the screen offers a normal phone call instead.
 */
public class CallActivity extends BaseActivity implements CallSession.Listener {

    public static final String EXTRA_CALL_ID = "call_id";
    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_PEER_NAME = "peer_name";
    public static final String EXTRA_OUTGOING = "outgoing";
    public static final String EXTRA_ANSWER = "answer";
    public static final String EXTRA_DECLINE = "decline";

    private static final long RING_TIMEOUT_MILLIS = 40_000;
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000;

    private ActivityCallBinding binding;
    private CallSession session;
    private String callId;
    private String orderId;
    private boolean outgoing;
    private boolean answered;
    private boolean connected;
    private boolean ended;
    private boolean muted;
    private boolean reconnecting;
    private boolean speaker;
    private long connectedAt;
    private Ringtone ringtone;
    private ToneGenerator ringback;
    private AudioManager audio;
    private ListenerRegistration ringingWatch;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ringTimeout = () -> {
        if (!answered && !ended) {
            if (outgoing && session != null) {
                session.hangUp(CallSession.STATE_MISSED);
            } else {
                endScreen(R.string.call_missed);
            }
        }
    };

    private final Runnable connectTimeout = () -> {
        if (!connected && !ended) {
            binding.callStatus.setText(R.string.call_cant_connect);
            binding.callPhone.setVisibility(View.VISIBLE);
        }
    };

    private final Runnable clock = new Runnable() {
        @Override
        public void run() {
            if (connected && !ended && reconnecting) {
                handler.postDelayed(this, 1000);
            } else if (connected && !ended) {
                long seconds = (SystemClock.elapsedRealtime() - connectedAt) / 1000;
                binding.callStatus.setText(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    /** Rings the other person on an order: the customer's rider, or the rider's customer. */
    public static void callAbout(Activity activity, Order order, String peerUid, String peerName) {
        if (!Cloud.isOn(activity) || peerUid == null || !order.hasRealRider()) {
            return;
        }
        Session me = new Session(activity);
        String callId = CallSession.ring(me.uid(), me.name(), peerUid, peerName, order.id);
        activity.startActivity(new Intent(activity, CallActivity.class)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_ORDER_ID, order.id)
                .putExtra(EXTRA_PEER_NAME, peerName)
                .putExtra(EXTRA_OUTGOING, true));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        audio = getSystemService(AudioManager.class);
        NotificationHelper.cancel(this, NotificationHelper.CALL_ID);

        callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        outgoing = getIntent().getBooleanExtra(EXTRA_OUTGOING, false);
        if (callId == null || !Cloud.isOn(this)) {
            finish();
            return;
        }
        String peer = getIntent().getStringExtra(EXTRA_PEER_NAME);
        binding.callName.setText(peer == null || peer.isEmpty() ? getString(R.string.app_name) : peer);
        binding.callHangup.setOnClickListener(v -> hangUp());
        binding.callAnswer.setOnClickListener(v -> answer());
        binding.callMute.setOnClickListener(v -> toggleMute());
        binding.callSpeaker.setOnClickListener(v -> toggleSpeaker());
        binding.callPhone.setOnClickListener(v -> callByPhone());

        if (getIntent().getBooleanExtra(EXTRA_DECLINE, false)) {
            CallSession.setState(callId, CallSession.STATE_DECLINED);
            CallSession.removeLater(callId);
            finish();
            return;
        }
        session = new CallSession(this, callId, new Session(this).uid(), outgoing, this);
        if (outgoing) {
            binding.callStatus.setText(R.string.call_ringing);
            withPermission(Manifest.permission.RECORD_AUDIO, R.string.permission_microphone, () -> {
                startAudio();
                playRingback();
                session.start();
            });
        } else {
            binding.callStatus.setText(R.string.incoming_call_title);
            binding.callAnswer.setVisibility(View.VISIBLE);
            playRingtone();
            // Until answered, only watch whether the caller gives up.
            ringingWatch = Cloud.db().collection(Cloud.CALLS).document(callId).addSnapshotListener((doc, e) -> {
                if (!answered && doc != null && (!doc.exists()
                        || !CallSession.STATE_RINGING.equals(doc.getString("state")))) {
                    endScreen(R.string.call_missed);
                }
            });
            if (getIntent().getBooleanExtra(EXTRA_ANSWER, false)) {
                answer();
            }
        }
        handler.postDelayed(ringTimeout, RING_TIMEOUT_MILLIS);
    }

    private void answer() {
        if (answered || ended) {
            return;
        }
        withPermission(Manifest.permission.RECORD_AUDIO, R.string.permission_microphone, () -> {
            answered = true;
            stopWatchingRing();
            stopRinging();
            binding.callAnswer.setVisibility(View.GONE);
            binding.callStatus.setText(R.string.call_connecting);
            startAudio();
            session.start();
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS);
        });
    }

    @SuppressWarnings("deprecation")
    private void startAudio() {
        if (audio != null) {
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audio.setSpeakerphoneOn(false);
        }
    }

    private void playRingtone() {
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        if (ringtone != null) {
            ringtone.play();
        }
    }

    private void playRingback() {
        try {
            ringback = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70);
            ringback.startTone(ToneGenerator.TONE_SUP_RINGTONE);
        } catch (RuntimeException e) {
            ringback = null;
        }
    }

    private void stopRinging() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (ringback != null) {
            ringback.stopTone();
            ringback.release();
            ringback = null;
        }
    }

    @Override
    public void onAnswered() {
        runOnUiThread(() -> {
            answered = true;
            stopRinging();
            binding.callStatus.setText(R.string.call_connecting);
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MILLIS);
        });
    }

    @Override
    public void onConnected() {
        connected = true;
        connectedAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(connectTimeout);
        binding.callPhone.setVisibility(View.GONE);
        binding.callControls.setVisibility(View.VISIBLE);
        handler.post(clock);
    }

    @Override
    public void onReconnecting() {
        reconnecting = true;
        binding.callStatus.setText(R.string.call_reconnecting);
    }

    @Override
    public void onReconnected() {
        reconnecting = false;
    }

    @Override
    public void onEnded(String reason) {
        if (outgoing && !connected && (CallSession.STATE_MISSED.equals(reason) || CallSession.STATE_DECLINED.equals(reason))) {
            leaveMissedCallNote();
        }
        int message;
        switch (reason) {
            case CallSession.STATE_DECLINED:
                message = R.string.call_declined;
                break;
            case CallSession.STATE_MISSED:
                message = R.string.call_no_answer;
                break;
            case "failed":
                message = R.string.call_cant_connect;
                break;
            default:
                message = R.string.call_ended;
        }
        if ("failed".equals(reason)) {
            ended = true;
            stopRinging();
            binding.callStatus.setText(message);
            binding.callPhone.setVisibility(View.VISIBLE);
            binding.callControls.setVisibility(View.GONE);
            binding.callAnswer.setVisibility(View.GONE);
            return;
        }
        endScreen(message);
    }

    /** So the other person sees they missed a call, even if they never saw it ring. */
    private void leaveMissedCallNote() {
        if (orderId == null) {
            return;
        }
        Session me = new Session(this);
        ChatMessage note = new ChatMessage(null, me.uid(), me.name(), getString(R.string.chat_missed_call),
                System.currentTimeMillis());
        Cloud.db().collection(Cloud.ORDERS).document(orderId).collection(Cloud.MESSAGES).add(note.toMap());
    }

    private void stopWatchingRing() {
        if (ringingWatch != null) {
            ringingWatch.remove();
            ringingWatch = null;
        }
    }

    private void endScreen(int message) {
        if (ended) {
            return;
        }
        ended = true;
        stopWatchingRing();
        stopRinging();
        binding.callStatus.setText(message);
        binding.callControls.setVisibility(View.GONE);
        binding.callAnswer.setVisibility(View.GONE);
        handler.postDelayed(this::finish, 1500);
    }

    private void hangUp() {
        if (ended) {
            finish();
            return;
        }
        if (session != null) {
            session.hangUp(!outgoing && !answered ? CallSession.STATE_DECLINED : CallSession.STATE_ENDED);
        } else {
            finish();
        }
    }

    private void toggleMute() {
        muted = !muted;
        session.setMuted(muted);
        binding.callMute.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
    }

    @SuppressWarnings("deprecation")
    private void toggleSpeaker() {
        speaker = !speaker;
        if (audio != null) {
            audio.setSpeakerphoneOn(speaker);
        }
        binding.callSpeaker.setAlpha(speaker ? 1f : 0.6f);
    }

    /** The fallback: the phone's own dialer with the other person's number. */
    private void callByPhone() {
        if (orderId == null) {
            return;
        }
        boolean rider = new Session(this).isRider();
        Context app = getApplicationContext();
        OrderStore.contact(orderId, contact -> {
            Object number = contact.get(rider ? "customerPhone" : "riderPhone");
            if (number == null || number.toString().isEmpty()) {
                Toast.makeText(app, R.string.no_phone_number, Toast.LENGTH_LONG).show();
                return;
            }
            if (session != null && !ended) {
                session.hangUp(CallSession.STATE_ENDED);
            }
            dial(number.toString());
            finish();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopWatchingRing();
        stopRinging();
        if (session != null && !ended) {
            session.hangUp(CallSession.STATE_ENDED);
        }
        if (audio != null) {
            audio.setSpeakerphoneOn(false);
            audio.setMode(AudioManager.MODE_NORMAL);
        }
        super.onDestroy();
    }
}
