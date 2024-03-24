package com.norman.webviewup.lib.service.proxy;

import com.norman.webviewup.lib.reflect.RuntimeProxy;
import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Method;


@ClassName(value = "android.webkit.IWebViewUpdateService$Stub")
public abstract class WebViewUpdateServiceProxy extends RuntimeProxy {

    public WebViewUpdateServiceProxy() {
        super();
    }

    @Method("waitForAndGetProvider")
    protected abstract Object waitForAndGetProvider();

    @Method("asBinder")
    protected abstract android.os.IBinder asBinder();

    @Method("isMultiProcessEnabled")
    protected abstract boolean isMultiProcessEnabled();


}
