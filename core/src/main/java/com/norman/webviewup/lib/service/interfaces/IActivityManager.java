package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.app.ActivityManager")
public interface IActivityManager {
    @Field(value = "IActivityManagerSingleton", type = Field.STATIC)
    Object getIActivityManagerSingleton();
}
