package com.norman.webviewup.demo;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.List;

/**
 * Demo-only helpers. {@link Application#onCreate()} runs in every process, including WebView stub
 * {@code :sandboxed_process*}; those must not run {@link com.norman.webviewup.lib.WebViewUpgrade}
 * (which creates a {@link android.webkit.WebView} and pins Chromium {@code LibraryProcessType}),
 * or {@code SandboxedProcessService.onBind} fails with "Trying to change the LibraryProcessType".
 */
final class DemoProcessUtils {

    private DemoProcessUtils() {
    }

    static boolean isMainProcess(Context context) {
        Context app = context.getApplicationContext();
        String packageName = app.getPackageName();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String current = Application.getProcessName();
            return packageName.equals(current);
        }
        int pid = Process.myPid();
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return true;
        }
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return true;
        }
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (info != null && info.pid == pid) {
                return packageName.equals(info.processName);
            }
        }
        return true;
    }
}
