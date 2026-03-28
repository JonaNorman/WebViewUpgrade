package com.norman.webviewup.lib.sandbox.stub;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.norman.webviewup.lib.sandbox.SandboxedProcessServiceDelegate;

public class StubSandboxedProcessService2 extends Service {

    private final SandboxedProcessServiceDelegate mDelegate =
            new SandboxedProcessServiceDelegate();

    @Override
    public void onCreate() {
        super.onCreate();
        mDelegate.onCreate(this, getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mDelegate.onBind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        mDelegate.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDelegate.onDestroy();
    }
}
