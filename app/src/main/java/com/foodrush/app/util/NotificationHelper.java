package com.foodrush.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.OrderLogic;
import com.foodrush.app.ui.CallActivity;
import com.foodrush.app.ui.ChatActivity;
import com.foodrush.app.ui.MainActivity;
import com.foodrush.app.ui.TrackingActivity;
import com.foodrush.app.ui.restaurant.KitchenActivity;
import com.foodrush.app.ui.rider.RiderHomeActivity;

public final class NotificationHelper {

    public static final String CHANNEL_ORDERS = "orders";
    public static final String CHANNEL_PROMOS = "promos";
    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_CALLS = "calls";
    public static final String CHANNEL_WORK = "work";
    public static final String CHANNEL_JOBS = "jobs";

    public static final int FOREGROUND_ID = 1;
    private static final int PROMO_ID = 2;
    public static final int RIDER_FOREGROUND_ID = 3;
    public static final int KITCHEN_FOREGROUND_ID = 4;
    public static final int CALL_ID = 5;
    private static final int NEW_JOB_ID = 6;

    private NotificationHelper() {
    }

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel orders = new NotificationChannel(CHANNEL_ORDERS,
                context.getString(R.string.channel_orders), NotificationManager.IMPORTANCE_HIGH);
        orders.enableLights(true);
        orders.setLightColor(Color.YELLOW);
        orders.enableVibration(true);
        manager.createNotificationChannel(orders);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_PROMOS,
                context.getString(R.string.channel_promos), NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_CHAT,
                context.getString(R.string.channel_chat), NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel calls = new NotificationChannel(CHANNEL_CALLS,
                context.getString(R.string.channel_calls), NotificationManager.IMPORTANCE_HIGH);
        calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        calls.enableVibration(true);
        manager.createNotificationChannel(calls);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_WORK,
                context.getString(R.string.channel_work), NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_JOBS,
                context.getString(R.string.channel_jobs), NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent activity(Context context, int requestCode, Intent intent) {
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openOrder(Context context, String orderId) {
        Intent intent = new Intent(context, TrackingActivity.class)
                .putExtra(TrackingActivity.EXTRA_ORDER_ID, orderId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return activity(context, orderId.hashCode(), intent);
    }

    public static String stageText(Context context, Order order) {
        switch (order.status) {
            case Order.CANCELLED:
                return order.cancelReason.isEmpty() ? context.getString(R.string.stage_cancelled_long)
                        : context.getString(R.string.stage_cancelled_reason, order.cancelReason);
            case Order.ACCEPTED:
                return context.getString(R.string.stage_accepted_long);
            case Order.PREPARING:
                return context.getString(R.string.stage_preparing_long);
            case Order.READY:
                return context.getString(order.riderUid == null ? R.string.stage_ready_waiting_long
                        : R.string.stage_ready_long);
            case Order.PICKED_UP:
                return context.getString(R.string.stage_picked_long);
            case Order.ARRIVING:
                return context.getString(R.string.stage_arriving_long);
            case Order.DELIVERED:
                return context.getString(R.string.stage_delivered_long);
            default:
                if (OrderLogic.waitingForSchedule(order, System.currentTimeMillis())) {
                    return context.getString(R.string.stage_scheduled_long,
                            android.text.format.DateFormat.getTimeFormat(context).format(new java.util.Date(order.scheduledFor)));
                }
                return context.getString(R.string.stage_placed_long);
        }
    }

    /** Ongoing notification the customer's tracking service must show while it runs. */
    public static NotificationCompat.Builder foreground(Context context, Order order) {
        return new NotificationCompat.Builder(context, CHANNEL_WORK)
                .setSmallIcon(R.drawable.ic_notify_rider)
                .setContentTitle(context.getString(R.string.notification_tracking_title))
                .setContentText(order == null ? "" : stageText(context, order))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(order == null ? null : openOrder(context, order.id));
    }

    /** Status change, shown with a custom RemoteViews layout and a "Track order" action. */
    public static void orderStatus(Context context, Order order) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_order);
        views.setTextViewText(R.id.notification_title, context.getString(R.string.notification_order_title));
        views.setTextViewText(R.id.notification_text, stageText(context, order));
        views.setImageViewResource(R.id.notification_icon,
                order.status == Order.DELIVERED ? R.drawable.ic_notify_check : R.drawable.ic_notify_rider);

        PendingIntent open = openOrder(context, order.id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.ic_notify_rider)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views)
                .setContentTitle(context.getString(R.string.notification_order_title))
                .setContentText(stageText(context, order))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(R.drawable.ic_notify_rider, context.getString(R.string.action_track_order), open);
        post(context, order.id.hashCode(), builder);
    }

    public static void promo(Context context, String title, String text) {
        PendingIntent open = activity(context, 0, new Intent(context, MainActivity.class));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_PROMOS)
                .setSmallIcon(R.drawable.ic_notify_offer)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true);
        post(context, PROMO_ID, builder);
    }

    public static void chatMessage(Context context, String orderId, String fromName, String text) {
        PendingIntent open = activity(context, ("chat" + orderId).hashCode(),
                new Intent(context, ChatActivity.class).putExtra(ChatActivity.EXTRA_ORDER_ID, orderId));
        Person sender = new Person.Builder().setName(fromName).build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CHAT)
                .setSmallIcon(R.drawable.ic_notify_chat)
                .setStyle(new NotificationCompat.MessagingStyle(sender).addMessage(text, System.currentTimeMillis(), sender))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        post(context, ("chat" + orderId).hashCode(), builder);
    }

    /** Ringing notification; full-screen on a locked phone. */
    public static void incomingCall(Context context, Intent callScreen, String callerName) {
        PendingIntent open = activity(context, 101, callScreen);
        PendingIntent answer = activity(context, 102,
                new Intent(callScreen).putExtra(CallActivity.EXTRA_ANSWER, true));
        PendingIntent decline = activity(context, 103,
                new Intent(callScreen).putExtra(CallActivity.EXTRA_DECLINE, true));
        Person caller = new Person.Builder().setName(callerName).setImportant(true).build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_notify_call)
                .setContentTitle(context.getString(R.string.incoming_call_title))
                .setContentText(callerName)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX);
        post(context, CALL_ID, builder);
    }

    public static void cancel(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }

    public static void newKitchenOrder(Context context, Order order) {
        PendingIntent open = activity(context, 201, new Intent(context, KitchenActivity.class));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_JOBS)
                .setSmallIcon(R.drawable.ic_notify_offer)
                .setContentTitle(context.getString(R.string.kitchen_new_order_title))
                .setContentText(order.items.replace('\n', ' '))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        post(context, ("kitchen" + order.id).hashCode(), builder);
    }

    public static void newDelivery(Context context, int count) {
        PendingIntent open = activity(context, 301, new Intent(context, RiderHomeActivity.class));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_JOBS)
                .setSmallIcon(R.drawable.ic_notify_rider)
                .setContentTitle(context.getString(R.string.rider_new_delivery_title))
                .setContentText(context.getResources().getQuantityString(R.plurals.rider_new_delivery_text, count, count))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        post(context, NEW_JOB_ID, builder);
    }

    /** Ongoing notification for the rider's location service and the restaurant's order watcher. */
    public static NotificationCompat.Builder work(Context context, Class<?> screen, String title, String text) {
        return new NotificationCompat.Builder(context, CHANNEL_WORK)
                .setSmallIcon(R.drawable.ic_notify_rider)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(activity(context, screen.getName().hashCode(), new Intent(context, screen)));
    }

    public static boolean canPost(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Guarded by canPost, which lint cannot see through. */
    @SuppressLint("MissingPermission")
    public static void post(Context context, int id, NotificationCompat.Builder builder) {
        if (canPost(context)) {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        }
    }
}
