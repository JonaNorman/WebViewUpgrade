package com.norman.webviewup.lib.service.binder;

import android.os.IBinder;


public abstract class BinderHook {

    private final Object sync = new Object();

    private IBinder originalBinder;

    private ProxyBinder proxyBinder;

    private boolean currentHook;
    private boolean recentHook;


    public BinderHook() {
    }

    public final void hook() {
        synchronized (sync) {
            if (currentHook) {
                return;
            }
            if (recentHook) {
                onProxyBinderReplace(proxyBinder);
            } else {
                IBinder original = onTargetBinderObtain();
                ProxyBinder proxy = onProxyBinderCreate(original);
                onProxyBinderReplace(proxy);
                this.originalBinder = original;
                this.proxyBinder = proxy;
                this.recentHook = true;
            }
            currentHook = true;
        }
    }

    public boolean isHook() {
        synchronized (sync) {
            return currentHook;
        }
    }

    public final boolean restore() {
        synchronized (sync) {
            if (!currentHook) {
                return false;
            }
            onTargetBinderRestore(originalBinder);
            currentHook = false;
            return true;
        }
    }

    protected ProxyBinder getProxyBinder() {
        return proxyBinder;
    }

    protected abstract IBinder onTargetBinderObtain();

    protected abstract ProxyBinder onProxyBinderCreate(IBinder binder);

    protected abstract void onTargetBinderRestore(IBinder binder);

    protected abstract void onProxyBinderReplace(ProxyBinder binder);
}
