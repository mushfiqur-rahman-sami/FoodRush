package com.foodrush.app.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ViewQuantityStepperBinding;

/** The design's "- 1 +" control. Shows "+ Add" at zero when app:minValue is 0. */
public class QuantityStepperView extends LinearLayout {

    public interface OnChangeListener {
        void onChange(int value);
    }

    private final ViewQuantityStepperBinding binding;
    private int minValue;
    private int maxValue = 20;
    private int value;
    private OnChangeListener listener;

    public QuantityStepperView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        binding = ViewQuantityStepperBinding.inflate(LayoutInflater.from(context), this);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.QuantityStepperView);
        minValue = a.getInt(R.styleable.QuantityStepperView_minValue, 1);
        a.recycle();
        value = minValue;
        binding.stepperMinus.setOnClickListener(v -> change(value - 1));
        binding.stepperPlus.setOnClickListener(v -> change(value + 1));
        binding.stepperAdd.setOnClickListener(v -> change(value + 1));
        render();
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.listener = listener;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        value = Math.max(minValue, Math.min(maxValue, newValue));
        render();
    }

    /** Allows the minus button to reach zero even when the view normally stops at one. */
    public void setMinValue(int min) {
        minValue = min;
        render();
    }

    private void change(int newValue) {
        int clamped = Math.max(minValue, Math.min(maxValue, newValue));
        if (clamped != value) {
            value = clamped;
            render();
            if (listener != null) {
                listener.onChange(value);
            }
        }
    }

    private void render() {
        boolean showAdd = minValue == 0 && value == 0;
        binding.stepperAdd.setVisibility(showAdd ? View.VISIBLE : View.GONE);
        binding.stepperControls.setVisibility(showAdd ? View.GONE : View.VISIBLE);
        binding.stepperValue.setText(String.valueOf(value));
    }
}
