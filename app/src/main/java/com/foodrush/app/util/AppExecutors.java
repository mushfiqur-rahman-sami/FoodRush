package com.foodrush.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Runs database and network work off the main thread and posts results back to it. */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static void io(Runnable work) {
        IO.execute(work);
    }

    public static void main(Runnable work) {
        MAIN.post(work);
    }

    public static <T> void load(Supplier<T> work, Consumer<T> onResult) {
        IO.execute(() -> {
            T result = work.get();
            MAIN.post(() -> onResult.accept(result));
        });
    }
}
