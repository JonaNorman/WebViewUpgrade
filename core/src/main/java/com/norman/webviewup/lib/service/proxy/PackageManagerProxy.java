package com.norman.webviewup.lib.service.proxy;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;

import com.norman.webviewup.lib.reflect.RuntimeProxy;
import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Method;


@ClassName("android.content.pm.IPackageManager$Stub")
public abstract class PackageManagerProxy extends RuntimeProxy {

    public PackageManagerProxy() {
        super();
    }


    @Method(value = "getPackageInfo", fuzzy = true)
    protected abstract PackageInfo getPackageInfo(Object... args);

    @Method(value = "getApplicationInfo", fuzzy = true)
    protected abstract ApplicationInfo getApplicationInfo(Object... args);

    @Method(value = "getServiceInfo", fuzzy = true)
    protected abstract ServiceInfo getServiceInfo(Object... args);

    @Method(value = "getComponentEnabledSetting", fuzzy = true)
    protected abstract int getComponentEnabledSetting(Object... args);

    @Method("getInstallerPackageName")
    protected abstract String getInstallerPackageName(String packageName);


    @Method("asBinder")
    protected abstract android.os.IBinder asBinder();

}
