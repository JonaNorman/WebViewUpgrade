package com.norman.webviewup.lib.service.interfaces;

import android.content.pm.PackageInfo;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;


@ClassName(value = "android.webkit.WebViewProviderResponse")
public interface IWebViewProviderResponse {

    @Field(value = "packageInfo")
    void setPackageInfo(PackageInfo packageInfo);

    @Field(value = "packageInfo")
    PackageInfo getPackageInfo();

}
