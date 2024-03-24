package com.norman.webviewup.lib.service.interfaces;


import android.os.IInterface;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.app.ActivityThread")
public interface IActivityThread {
    @Field(value = "sPackageManager", type = Field.STATIC)
    void setPackageManager(IInterface iInterface);

    @Field(value = "sPackageManager", type = Field.STATIC)
    IInterface getPackageManager();
}
