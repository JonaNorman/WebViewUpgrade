package com.norman.webviewup.lib.service.interfaces;

import android.os.IBinder;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;
import com.norman.webviewup.lib.reflect.annotation.Method;

import java.util.Map;

@ClassName(value = "android.os.ServiceManager")
public interface IServiceManager {

    @Method(value = "getService", type = Method.STATIC)
    IBinder getService(String name);

    @Field(value = "sCache", type = Field.STATIC)
    Map<String, IBinder> getServiceCache();
}
