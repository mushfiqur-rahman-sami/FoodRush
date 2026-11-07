package com.foodrush.app.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.foodrush.app.R;

/** Five canvas-drawn stars with partial fill. Tappable when app:editable is true. */
public class RatingBarView extends View {

    private static final int STARS = 5;

    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private float rating;
    private boolean editable;

    public RatingBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        emptyPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        fillPaint.setColor(ContextCompat.getColor(context, R.color.amber));
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RatingBarView);
        rating = a.getFloat(R.styleable.RatingBarView_rating, 0f);
        editable = a.getBoolean(R.styleable.RatingBarView_editable, false);
        a.recycle();
    }

    public void setRating(float value) {
        rating = Math.max(0f, Math.min(STARS, value));
        invalidate();
    }

    public float getRating() {
        return rating;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = resolveSize((int) (18 * getResources().getDisplayMetrics().density), heightSpec);
        setMeasuredDimension(height * STARS, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = getHeight();
        for (int i = 0; i < STARS; i++) {
            buildStar(i * size + size / 2, size / 2, size * 0.46f);
            canvas.drawPath(star, emptyPaint);
            float fill = Math.max(0f, Math.min(1f, rating - i));
            if (fill > 0) {
                int saved = canvas.save();
                canvas.clipRect(i * size, 0, i * size + size * fill, size);
                canvas.drawPath(star, fillPaint);
                canvas.restoreToCount(saved);
            }
        }
    }

    private void buildStar(float cx, float cy, float outer) {
        star.reset();
        float inner = outer * 0.45f;
        for (int point = 0; point < 10; point++) {
            double angle = Math.toRadians(-90 + point * 36);
            float radius = point % 2 == 0 ? outer : inner;
            float x = cx + (float) (radius * Math.cos(angle));
            float y = cy + (float) (radius * Math.sin(angle));
            if (point == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.close();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            setRating((float) Math.ceil(event.getX() / getHeight()));
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
