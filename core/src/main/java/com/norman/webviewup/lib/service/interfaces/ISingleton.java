package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.util.Singleton")
public interface ISingleton {
    @Field("mInstance")
    Object getInstance();

    @Field("mInstance")
    void setInstance(Object instance);
}
