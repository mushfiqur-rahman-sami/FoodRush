package com.foodrush.app.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.foodrush.app.R;
import com.foodrush.app.data.Catalog;
import com.foodrush.app.data.Session;
import com.foodrush.app.model.Promo;
import com.foodrush.app.util.NotificationHelper;

import java.util.List;

/** Daily background job: announces the next offer in rotation, if the user wants promo alerts. */
public class PromoRefreshWorker extends Worker {

    public PromoRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Session session = new Session(context);
        List<Promo> promos = Catalog.get(context).promos;
        if (!session.promoNotifications() || promos.isEmpty()) {
            return Result.success();
        }
        int next = 0;
        for (int i = 0; i < promos.size(); i++) {
            if (promos.get(i).code.equals(session.lastPromoCode())) {
                next = (i + 1) % promos.size();
            }
        }
        Promo promo = promos.get(next);
        session.setLastPromoCode(promo.code);
        NotificationHelper.promo(context, promo.title + " · " + promo.code,
                context.getString(R.string.promo_notification_text, promo.subtitle));
        return Result.success();
    }
}
