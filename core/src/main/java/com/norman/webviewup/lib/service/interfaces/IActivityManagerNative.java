package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.app.ActivityManagerNative")
public interface IActivityManagerNative {
    @Field(value = "gDefault", type = Field.STATIC)
    Object getGDefault();
}
