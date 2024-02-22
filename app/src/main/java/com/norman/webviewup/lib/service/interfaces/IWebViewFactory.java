package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.webkit.WebViewFactory")
public interface IWebViewFactory {


    @Field(value = "sProviderLock", type = Field.STATIC)
    Object getProviderLock();

    @Field(value = "sProviderInstance", type = Field.STATIC)
    Object getProviderInstance();

    @Field(value = "sProviderInstance", type = Field.STATIC)
    void setProviderInstance(Object instance);
}
