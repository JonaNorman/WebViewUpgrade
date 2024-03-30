package com.norman.webviewup.lib.util;

import android.os.Handler;
import android.os.Looper;

public class HandlerUtils {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void runInMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }
}
