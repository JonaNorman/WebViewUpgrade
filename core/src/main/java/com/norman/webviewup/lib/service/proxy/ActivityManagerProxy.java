package com.norman.webviewup.lib.service.proxy;

import com.norman.webviewup.lib.reflect.RuntimeProxy;
import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Method;

/**
 * IActivityManager 的代理基类。
 *
 * bindIsolatedService / bindService / bindServiceInstance 的参数在 Android 8 ~ 15 之间
 * 频繁增删参数，因此统一标注 fuzzy=true，由子类通过遍历 invokeContext.args 取出 Intent。
 */
@ClassName("android.app.IActivityManager")
public abstract class ActivityManagerProxy extends RuntimeProxy {

    public ActivityManagerProxy() {
        super();
    }

    /**
     * 拦截所有 bindIsolatedService 变体（fuzzy 模式，忽略参数数量）。
     * invokeContext.args 包含系统传入的所有原始参数，子类遍历找 Intent 即可。
     */
    @Method(value = "bindIsolatedService", fuzzy = true)
    protected abstract Object bindIsolatedService(Object... args);

    /**
     * 拦截 Android 14+ 新增的 bindServiceInstance 变体（同为 fuzzy）。
     */
    @Method(value = "bindServiceInstance", fuzzy = true)
    protected abstract Object bindServiceInstance(Object... args);
}
