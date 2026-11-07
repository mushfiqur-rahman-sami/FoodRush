package com.foodrush.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.foodrush.app.R;

import java.util.Locale;

/** Canvas-drawn bar chart for the dashboards: one rounded bar per day, the value above and the day below. */
public class BarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float density;
    private int[] values = new int[0];
    private String[] labels = new String[0];

    public BarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        barPaint.setColor(ContextCompat.getColor(context, R.color.amber));
        todayPaint.setColor(ContextCompat.getColor(context, R.color.amber_dark));
        emptyPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(10 * scaled);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_medium));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(11 * scaled);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_regular));
    }

    /** The last value is today and is drawn darker. */
    public void setData(int[] newValues, String[] newLabels) {
        values = newValues;
        labels = newLabels;
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            description.append(labels[i]).append(' ').append(values[i]).append(". ");
        }
        setContentDescription(description.toString());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize((int) (170 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (values.length == 0) {
            return;
        }
        int max = 1;
        for (int v : values) {
            max = Math.max(max, v);
        }
        float slot = (float) getWidth() / values.length;
        float top = 20 * density;
        float bottom = getHeight() - 22 * density;
        float width = Math.min(28 * density, slot * 0.55f);
        float radius = 6 * density;
        for (int i = 0; i < values.length; i++) {
            float centre = slot * i + slot / 2;
            float height = Math.max(3 * density, (bottom - top) * values[i] / max);
            bar.set(centre - width / 2, bottom - height, centre + width / 2, bottom);
            Paint paint = values[i] == 0 ? emptyPaint : i == values.length - 1 ? todayPaint : barPaint;
            canvas.drawRoundRect(bar, radius, radius, paint);
            if (values[i] > 0) {
                canvas.drawText(shortNumber(values[i]), centre, bar.top - 5 * density, valuePaint);
            }
            canvas.drawText(labels[i], centre, getHeight() - 5 * density, labelPaint);
        }
    }

    /** 1250 shows as "1.3k" so bars stay readable. */
    static String shortNumber(int value) {
        return value >= 1000 ? String.format(Locale.US, "%.1fk", value / 1000f) : String.valueOf(value);
    }
}
