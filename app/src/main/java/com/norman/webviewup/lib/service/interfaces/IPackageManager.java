package com.norman.webviewup.lib.service.interfaces;

import android.os.IInterface;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Method;

@ClassName("android.content.pm.IPackageManager$Stub")
public interface IPackageManager {

    String SERVICE = "package";
    @Method(value = "asInterface", type = Method.STATIC)
    IInterface asInterface(android.os.IBinder obj);
}
