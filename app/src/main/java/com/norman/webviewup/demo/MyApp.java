package com.norman.webviewup.demo;

import android.app.Application;

import com.norman.webviewup.lib.WebViewUpgrade;
import com.norman.webviewup.lib.source.UpgradeSource;
import com.norman.webviewup.lib.util.ProcessUtils;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (!ProcessUtils.isMainProcess(this)) {
            return;
        }
        UpgradeSource source = PreferredWebViewStore.toUpgradeSource(this);
        if (source != null) {
            WebViewUpgrade.upgrade(source);
        }
    }
}
