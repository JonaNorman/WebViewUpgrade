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


    @Method("getPackageInfo")
    protected abstract PackageInfo getPackageInfo(String packageName, long flags, int userId);

    @Method("getPackageInfo")
    protected abstract PackageInfo getPackageInfo(String packageName, int flags, int userId);


    @Method("getPackageInfo")
    protected abstract PackageInfo getPackageInfo(String packageName, int flags);

    @Method("getApplicationInfo")
    protected abstract ApplicationInfo getApplicationInfo(String packageName, long flags, int userId);

    @Method("getApplicationInfo")
    protected abstract ApplicationInfo getApplicationInfo(String packageName, int flags, int userId);

    @Method("getServiceInfo")
    protected abstract ServiceInfo getServiceInfo(ComponentName component, int flags, int userId);

    @Method("getServiceInfo")
    protected abstract ServiceInfo getServiceInfo(ComponentName component, long flags, int userId);

    @Method("getComponentEnabledSetting")
    protected abstract int getComponentEnabledSetting(ComponentName componentName, int userId);

    @Method("getComponentEnabledSetting")
    protected abstract int getComponentEnabledSetting(ComponentName componentName);

    @Method("getInstallerPackageName")
    protected abstract String getInstallerPackageName(String packageName);


    @Method("asBinder")
    protected abstract android.os.IBinder asBinder();

}
