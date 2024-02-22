package com.norman.webviewup.lib.service.interfaces;

import android.content.pm.PackageManager;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.app.ContextImpl")
public interface IContextImpl {
    @Field("mPackageManager")
    void setPackageManager(PackageManager packageManager);
}
