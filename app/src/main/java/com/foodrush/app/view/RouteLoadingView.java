package com.foodrush.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.foodrush.app.R;

/**
 * Canvas-drawn loader for checkout: a scooter rides a winding dashed road from the restaurant pin to
 * the home pin, while both pins pulse. It loops until {@link #stop()}.
 */
public class RouteLoadingView extends View {

    private static final long LOOP_MILLIS = 2200;

    private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path road = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] position = new float[2];
    private final float density;
    private final Drawable scooter;
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float progress;

    public RouteLoadingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        int amber = ContextCompat.getColor(context, R.color.amber);
        int amberDark = ContextCompat.getColor(context, R.color.amber_dark);
        roadPaint.setStyle(Paint.Style.STROKE);
        roadPaint.setStrokeCap(Paint.Cap.ROUND);
        roadPaint.setStrokeWidth(14 * density);
        roadPaint.setColor(ContextCompat.getColor(context, R.color.surface_soft));
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(3 * density);
        dashPaint.setColor(amberDark);
        pulsePaint.setColor(amber);
        pinPaint.setColor(amberDark);
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_rider);
        scooter = icon == null ? null : DrawableCompat.wrap(icon.mutate());
        if (scooter != null) {
            DrawableCompat.setTint(scooter, ContextCompat.getColor(context, R.color.text_primary));
        }
        animator.setDuration(LOOP_MILLIS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            // The centre line slides backwards, so the road seems to move under the scooter.
            dashPaint.setPathEffect(new DashPathEffect(new float[]{12 * density, 10 * density}, -progress * 44 * density));
            invalidate();
        });
        setContentDescription(context.getString(R.string.checkout_working));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize((int) (150 * density), heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float margin = 34 * density;
        float mid = h * 0.58f;
        road.reset();
        road.moveTo(margin, mid);
        road.cubicTo(w * 0.33f, mid - 70 * density, w * 0.5f, mid + 60 * density, w * 0.7f, mid - 10 * density);
        road.quadTo(w * 0.82f, mid - 40 * density, w - margin, mid - 20 * density);
        measure.setPath(road, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    public void stop() {
        animator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float length = measure.getLength();
        if (length == 0) {
            return;
        }
        canvas.drawPath(road, roadPaint);
        canvas.drawPath(road, dashPaint);

        drawPin(canvas, 0f, progress);
        drawPin(canvas, length, (progress + 0.5f) % 1f);

        measure.getPosTan(length * easeInOut(progress), position, null);
        if (scooter != null) {
            int half = (int) (18 * density);
            float bob = (float) Math.sin(progress * Math.PI * 8) * 2 * density;
            scooter.setBounds((int) position[0] - half, (int) (position[1] - 2 * half + bob),
                    (int) position[0] + half, (int) (position[1] + bob));
            scooter.draw(canvas);
        }
    }

    private void drawPin(Canvas canvas, float distance, float pulse) {
        measure.getPosTan(distance, position, null);
        pulsePaint.setAlpha((int) (110 * (1 - pulse)));
        canvas.drawCircle(position[0], position[1], (8 + 16 * pulse) * density, pulsePaint);
        canvas.drawCircle(position[0], position[1], 7 * density, pinPaint);
    }

    private static float easeInOut(float t) {
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
    }
}
