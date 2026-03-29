package com.norman.webviewup.lib.util;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IVMRuntime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ProcessUtils {

    private static String currentInstructionSet = null;

    /**
     * {@link Application#onCreate()} runs in every process when using WebView multiprocess stubs
     * (e.g. {@code :sandboxed_process*}). Call {@link com.norman.webviewup.lib.WebViewUpgrade#upgrade}
     * only when this returns true for the main process.
     */
    public static boolean isMainProcess(Context context) {
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

    public static boolean is64Bit() {
        boolean process64bit = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            process64bit = Process.is64Bit();
        } else {
            try {
                IVMRuntime vmRuntime = RuntimeAccess.staticAccess(IVMRuntime.class);
                vmRuntime = RuntimeAccess.objectAccess(IVMRuntime.class, vmRuntime.getRuntime());
                process64bit = vmRuntime.is64Bit();
            } catch (Throwable ignore) {

            }
        }
        return process64bit;
    }

    public static String getCurrentInstruction() {
        if (currentInstructionSet != null) {
            return currentInstructionSet;
        }
        try {
            IVMRuntime ivmRuntime = RuntimeAccess.staticAccess(IVMRuntime.class);
            currentInstructionSet = ivmRuntime.getCurrentInstructionSet();
        } catch (Throwable throwable) {
            String[] abiSearchArr = new String[]{"mips64", "mips", "x86_64", "x86", "arm64-v8a", "armeabi-v7a", "armeabi"};
            Arrays.sort(abiSearchArr);
            for (String search : abiSearchArr) {
                int result = Arrays.binarySearch(Build.SUPPORTED_ABIS, search);
                if (result >= 0) {
                    if (search.equals("armeabi") || search.equals("armeabi-v7a")) {
                        currentInstructionSet = "arm";
                    } else if (search.equals("arm64-v8a")) {
                        currentInstructionSet = "arm64";
                    } else {
                        currentInstructionSet = search;
                    }
                    break;
                }
            }

        }
        return currentInstructionSet;
    }
}
