package com.norman.webviewup.lib.util;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Hard-restarts the app process so {@link com.norman.webviewup.lib.WebViewUpgrade} static state
 * resets. Runs {@link ActivityManager#killBackgroundProcesses(String)} first so stub
 * {@code :sandboxed_process*} child processes are more likely to exit before the new main process
 * runs {@code upgrade()}.
 * <p>
 * <b>Permission:</b> Host apps must declare {@code android.permission.KILL_BACKGROUND_PROCESSES}
 * in {@code AndroidManifest.xml} when using this class.
 */
public final class RestartUtil {

    private RestartUtil() {
    }

    public static void restart(@NonNull Application app) {
        restart(app, null);
    }

    public static void restart(@NonNull Application app, @Nullable Runnable preRestart) {
        if (preRestart != null) {
            try {
                preRestart.run();
            } catch (Throwable ignored) {
            }
        }
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.killBackgroundProcesses(app.getPackageName());
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                app.startActivity(intent);
            }
            System.exit(0);
        }, 120);
    }
}
