package com.foodrush.app.ui.rider;

import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.foodrush.app.R;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityRiderDashboardBinding;
import com.foodrush.app.databinding.ItemRatedDeliveryBinding;
import com.foodrush.app.model.Order;
import com.foodrush.app.order.Earnings;
import com.foodrush.app.order.OrderStore;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.DashboardViews;
import com.foodrush.app.util.PriceCalculator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The rider's income dashboard: today's goal, earnings by period with a 7-day chart, cash in hand
 * from cash-on-delivery orders, the combined rating with a star breakdown, and each delivery's
 * earning, stars and comment.
 */
public class RiderDashboardActivity extends BaseActivity {

    private static final int DAYS = 7;
    private static final int SHOWN_DELIVERIES = 30;

    private ActivityRiderDashboardBinding binding;
    private Session session;
    private OrderStore.Subscription subscription;
    private List<Order> delivered = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRiderDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setUpTopBar(binding.dashBar, R.string.rider_dashboard_title);
        session = new Session(this);
        binding.dashGoalSet.setOnClickListener(v -> editGoal());
        binding.dashCashDeposit.setOnClickListener(v -> confirmDeposit());
        loadDeposit();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Cloud.isOn(this)) {
            subscription = OrderStore.watchRider(this, session.uid(), orders -> {
                List<Order> done = new ArrayList<>();
                for (Order o : orders) {
                    if (o.status == Order.DELIVERED) {
                        done.add(o);
                    }
                }
                Collections.sort(done, (a, b) -> Long.compare(b.deliveredAt, a.deliveredAt));
                delivered = done;
                render();
            });
        }
        render();
    }

    @Override
    protected void onStop() {
        if (subscription != null) {
            subscription.remove();
            subscription = null;
        }
        super.onStop();
    }

    /** The deposit mark is also kept in the rider's cloud record, so it follows them to a new phone. */
    private void loadDeposit() {
        if (!Cloud.isOn(this)) {
            return;
        }
        Cloud.db().collection(Cloud.RIDERS).document(session.uid()).get().addOnSuccessListener(doc -> {
            Long cloud = doc.getLong("cashDepositedUntil");
            if (cloud != null && cloud > session.cashDepositedUntil()) {
                session.setCashDepositedUntil(cloud);
                render();
            }
        });
    }

    private void render() {
        long now = System.currentTimeMillis();
        Earnings.Amount earning = Earnings.riderEarning();
        Earnings.When when = Earnings.delivered();
        long today = Earnings.startOfDay(now);

        int todayTaka = Earnings.sum(delivered, earning, when, today);
        int todayCount = Earnings.count(delivered, when, today);
        renderGoal(todayCount, todayTaka);

        binding.dashEarnings.removeAllViews();
        DashboardViews.addTile(binding.dashEarnings, PriceCalculator.format(todayTaka), getString(R.string.period_today));
        DashboardViews.addTile(binding.dashEarnings,
                PriceCalculator.format(Earnings.sum(delivered, earning, when, Earnings.daysAgo(now, 7))),
                getString(R.string.period_week));
        DashboardViews.addTile(binding.dashEarnings,
                PriceCalculator.format(Earnings.sum(delivered, earning, when, Earnings.startOfMonth(now))),
                getString(R.string.period_month));
        DashboardViews.addTile(binding.dashEarnings, PriceCalculator.format(Earnings.sum(delivered, earning, when, 0)),
                getString(R.string.period_all));
        binding.dashChart.setData(Earnings.byDay(delivered, earning, when, DAYS, now),
                DashboardViews.dayLabels(this, now, DAYS));

        int cash = Earnings.cashInHand(delivered, session.cashDepositedUntil());
        binding.dashCashAmount.setText(PriceCalculator.format(cash));
        binding.dashCashNote.setText(session.cashDepositedUntil() == 0 ? getString(R.string.cash_never_deposited)
                : getString(R.string.cash_deposited_at,
                DateUtils.getRelativeTimeSpanString(session.cashDepositedUntil())));
        binding.dashCashDeposit.setEnabled(cash > 0);

        renderRatings();

        binding.dashStats.removeAllViews();
        double km = 0;
        for (Order o : delivered) {
            km += o.distanceKm;
        }
        int tips = Earnings.sum(delivered, o -> o.tip, when, 0);
        DashboardViews.addTile(binding.dashStats, String.valueOf(delivered.size()), getString(R.string.stat_deliveries));
        DashboardViews.addTile(binding.dashStats, String.format(Locale.US, "%.1f km", km), getString(R.string.stat_distance));
        DashboardViews.addTile(binding.dashStats,
                getString(R.string.minutes_short, Earnings.averageMinutes(delivered, o -> o.pickedUpAt, o -> o.deliveredAt)),
                getString(R.string.stat_avg_delivery));
        DashboardViews.addTile(binding.dashStats, PriceCalculator.format(tips), getString(R.string.stat_tips));

        renderDeliveries();
    }

    private void renderGoal(int count, int taka) {
        int goalCount = session.riderGoalDeliveries();
        int goalTaka = session.riderGoalTaka();
        // Progress follows whichever goal is further along.
        float byCount = goalCount > 0 ? (float) count / goalCount : 0f;
        float byTaka = goalTaka > 0 ? (float) taka / goalTaka : 0f;
        int percent = (int) Math.min(100, Math.round(Math.max(byCount, byTaka) * 100));
        binding.dashGoalProgress.setProgress(percent);
        binding.dashGoalText.setText(percent >= 100 ? getString(R.string.goal_done, count, PriceCalculator.format(taka))
                : getString(R.string.goal_progress, count, goalCount, PriceCalculator.format(taka),
                PriceCalculator.format(goalTaka)));
    }

    private void renderRatings() {
        Earnings.Ratings ratings = Earnings.riderRatings(delivered);
        binding.dashRating.setText(ratings.count == 0 ? "–" : String.format(Locale.US, "%.1f", ratings.average));
        binding.dashRatingStars.setRating(ratings.average);
        binding.dashRatingCount.setText(getResources().getQuantityString(R.plurals.ratings_count, ratings.count,
                ratings.count));
        LinearLayout rows = binding.dashRatingRows;
        rows.removeAllViews();
        for (int stars = 5; stars >= 1; stars--) {
            DashboardViews.addRatingRow(rows, stars, ratings.counts[stars - 1], ratings.count);
        }
    }

    private void renderDeliveries() {
        binding.dashDeliveries.removeAllViews();
        binding.dashDeliveriesEmpty.setVisibility(delivered.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (Order o : delivered.subList(0, Math.min(SHOWN_DELIVERIES, delivered.size()))) {
            ItemRatedDeliveryBinding row = ItemRatedDeliveryBinding.inflate(inflater, binding.dashDeliveries, true);
            List<String> names = new ArrayList<>();
            for (Order.Kitchen k : o.kitchens) {
                names.add(k.name);
            }
            row.ratedTitle.setText(String.join(" + ", names));
            row.ratedAmount.setText(PriceCalculator.format(o.fee + o.tip));
            String tip = o.tip > 0 ? getString(R.string.tip_included, PriceCalculator.format(o.tip)) : "";
            row.ratedWhen.setText(getString(R.string.delivery_when,
                    DateUtils.getRelativeTimeSpanString(o.deliveredAt), o.distanceKm, tip));
            boolean rated = o.riderRating > 0;
            row.ratedStars.setVisibility(rated ? View.VISIBLE : View.GONE);
            row.ratedStars.setRating(o.riderRating);
            row.ratedComment.setText(!rated ? getString(R.string.not_rated_yet)
                    : o.riderComment.isEmpty() ? getString(R.string.no_comment) : "“" + o.riderComment + "”");
        }
    }

    private void editGoal() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText deliveries = new EditText(this);
        deliveries.setInputType(InputType.TYPE_CLASS_NUMBER);
        deliveries.setHint(R.string.goal_deliveries_hint);
        deliveries.setText(String.valueOf(session.riderGoalDeliveries()));
        EditText taka = new EditText(this);
        taka.setInputType(InputType.TYPE_CLASS_NUMBER);
        taka.setHint(R.string.goal_taka_hint);
        taka.setText(String.valueOf(session.riderGoalTaka()));
        form.addView(deliveries);
        form.addView(taka);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.goal_title)
                .setMessage(R.string.goal_message)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    session.setRiderGoal(number(deliveries), number(taka));
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static int number(EditText field) {
        try {
            return Math.max(0, Integer.parseInt(field.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void confirmDeposit() {
        int cash = Earnings.cashInHand(delivered, session.cashDepositedUntil());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mark_deposited)
                .setMessage(getString(R.string.deposit_confirm, PriceCalculator.format(cash)))
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    long now = System.currentTimeMillis();
                    session.setCashDepositedUntil(now);
                    if (Cloud.isOn(this)) {
                        Map<String, Object> update = new HashMap<>();
                        update.put("cashDepositedUntil", now);
                        Cloud.db().collection(Cloud.RIDERS).document(session.uid()).set(update, SetOptions.merge());
                    }
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
