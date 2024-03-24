package com.norman.webviewup.lib.service.interfaces;

import android.content.pm.PackageInfo;
import android.os.IInterface;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Method;

@ClassName(value = "android.webkit.IWebViewUpdateService$Stub")
public interface IWebViewUpdateService {
    String SERVICE = "webviewupdate";

    @Method(value = "asInterface", type = Method.STATIC)
    IInterface asInterface(android.os.IBinder obj);

    @Method(value = "getCurrentWebViewPackage")
    PackageInfo getCurrentWebViewPackage();

}

