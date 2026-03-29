package com.norman.webviewup.demo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Restarts the whole app process so {@link com.norman.webviewup.lib.WebViewUpgrade} static state
 * resets. Calls {@link ActivityManager#killBackgroundProcesses(String)} first so stub
 * {@code :sandboxed_process*} child processes from the WebView stack are more likely to exit
 * before the new main process runs {@code upgrade()}.
 */
public final class DemoAppRestarter {

    private DemoAppRestarter() {
    }

    public static void restart(Application app) {
        DemoWebViewHolder.destroyHeldWebView();
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
