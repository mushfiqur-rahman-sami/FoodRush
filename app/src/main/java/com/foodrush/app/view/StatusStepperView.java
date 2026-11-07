package com.foodrush.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.foodrush.app.R;

/** Canvas-drawn order timeline: five dots joined by a line, filled up to the current stage. */
public class StatusStepperView extends View {

    private final String[] labels;
    private final Paint donePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int stage;

    public StatusStepperView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        labels = new String[]{context.getString(R.string.stage_placed), context.getString(R.string.stage_accepted),
                context.getString(R.string.stage_cooking), context.getString(R.string.stage_on_way),
                context.getString(R.string.stage_delivered)};
        donePaint.setColor(ContextCompat.getColor(context, R.color.amber));
        donePaint.setStrokeWidth(3 * density);
        todoPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        todoPaint.setStrokeWidth(3 * density);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        labelPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(ResourcesCompat.getFont(context, R.font.poppins_regular));
    }

    /** -1 greys out every step, for a cancelled order. */
    public void setStage(int value) {
        if (stage != value) {
            stage = value;
            invalidate();
            setContentDescription(value < 0 ? null : labels[Math.min(labels.length - 1, value)]);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize((int) (48 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = labels.length;
        float slot = (float) getWidth() / count;
        float y = 12 * density;
        float radius = 7 * density;
        for (int i = 0; i + 1 < count; i++) {
            float x1 = slot * i + slot / 2;
            canvas.drawLine(x1, y, x1 + slot, y, i < stage ? donePaint : todoPaint);
        }
        for (int i = 0; i < count; i++) {
            float x = slot * i + slot / 2;
            canvas.drawCircle(x, y, radius, i <= stage ? donePaint : todoPaint);
            canvas.drawText(labels[i], x, y + 26 * density, labelPaint);
        }
    }
}
