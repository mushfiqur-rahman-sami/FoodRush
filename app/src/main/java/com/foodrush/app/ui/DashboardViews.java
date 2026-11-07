package com.foodrush.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ItemStatTileBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Building blocks shared by the rider and restaurant dashboards. */
public final class DashboardViews {

    private DashboardViews() {
    }

    /** A value with a label underneath, filling one of the grid's two columns. */
    public static void addTile(GridLayout grid, String value, String label) {
        ItemStatTileBinding tile = ItemStatTileBinding.inflate(LayoutInflater.from(grid.getContext()), grid, false);
        tile.tileValue.setText(value);
        tile.tileLabel.setText(label);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        params.width = 0;
        int margin = (int) (5 * grid.getResources().getDisplayMetrics().density);
        params.setMargins(margin, margin, margin, margin);
        grid.addView(tile.getRoot(), params);
    }

    /** Short day names for the last {@code days} days, oldest first: "Fri", "Sat", ... "Today". */
    public static String[] dayLabels(Context context, long now, int days) {
        String[] labels = new String[days];
        SimpleDateFormat format = new SimpleDateFormat("EEE", Locale.ENGLISH);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        for (int i = days - 1; i >= 0; i--) {
            labels[i] = i == days - 1 ? context.getString(R.string.today) : format.format(c.getTime());
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return labels;
    }

    /** One line of the star breakdown: "5★", a bar, and how many. */
    public static void addRatingRow(LinearLayout parent, int stars, int count, int total) {
        Context context = parent.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (2 * density), 0, (int) (2 * density));

        TextView label = new TextView(context);
        label.setText(context.getString(R.string.stars_label, stars));
        label.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        label.setTextSize(12);
        label.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_regular));
        label.setMinWidth((int) (28 * density));
        row.addView(label);

        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(1, total));
        bar.setProgress(count);
        bar.setProgressTintList(ContextCompat.getColorStateList(context, R.color.amber_dark));
        bar.setProgressBackgroundTintList(ContextCompat.getColorStateList(context, R.color.divider));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, (int) (8 * density), 1f);
        barParams.setMargins((int) (6 * density), 0, (int) (8 * density), 0);
        row.addView(bar, barParams);

        TextView number = new TextView(context);
        number.setText(String.valueOf(count));
        number.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        number.setTextSize(12);
        number.setMinWidth((int) (22 * density));
        row.addView(number);
        parent.addView(row);
    }
}
