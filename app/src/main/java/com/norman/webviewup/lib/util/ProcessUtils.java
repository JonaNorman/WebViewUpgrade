package com.norman.webviewup.lib.util;

import android.os.Build;
import android.os.Process;

import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IVMRuntime;

public class ProcessUtils {

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
}
