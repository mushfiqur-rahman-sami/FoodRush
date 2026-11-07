package com.foodrush.app.util;

import android.view.View;

import androidx.annotation.StringRes;

import com.foodrush.app.R;
import com.foodrush.app.databinding.ViewStateBinding;

/** Loading, empty and error states for any list screen. */
public final class States {

    private States() {
    }

    public static void loading(ViewStateBinding state) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.VISIBLE);
        state.stateMessage.setVisibility(View.GONE);
        state.stateRetry.setVisibility(View.GONE);
    }

    public static void hide(ViewStateBinding state) {
        state.getRoot().setVisibility(View.GONE);
    }

    public static void empty(ViewStateBinding state, @StringRes int message) {
        empty(state, state.getRoot().getContext().getString(message));
    }

    public static void empty(ViewStateBinding state, CharSequence message) {
        state.getRoot().setVisibility(View.VISIBLE);
        state.stateProgress.setVisibility(View.GONE);
        state.stateMessage.setVisibility(View.VISIBLE);
        state.stateMessage.setText(message);
        state.stateRetry.setVisibility(View.GONE);
    }

    public static void error(ViewStateBinding state, Runnable retry) {
        empty(state, R.string.load_error);
        state.stateRetry.setVisibility(View.VISIBLE);
        state.stateRetry.setOnClickListener(v -> retry.run());
    }
}
