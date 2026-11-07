package com.foodrush.app.service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.foodrush.app.R;
import com.foodrush.app.data.Session;
import com.foodrush.app.util.NotificationHelper;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Shows promo pushes sent from the Firebase console (free). Needs google-services.json. */
public class FoodRushMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        if (!new Session(this).promoNotifications()) {
            return;
        }
        RemoteMessage.Notification body = message.getNotification();
        String title = body != null && body.getTitle() != null ? body.getTitle() : getString(R.string.app_name);
        String text = body != null && body.getBody() != null ? body.getBody() : "";
        NotificationHelper.promo(this, title, text);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d("FoodRushMessaging", "New FCM token received");
    }
}
