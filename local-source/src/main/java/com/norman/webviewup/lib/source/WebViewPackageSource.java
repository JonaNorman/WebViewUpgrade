package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class WebViewPackageSource extends WebViewSource {

    private final String packageName;
    private final Context context;

    private int flags;

    private PackageInfo packageInfo;

    public WebViewPackageSource(Context context, String packageName) {
        this.context = context == null ? null : context.getApplicationContext();
        this.packageName = packageName;
    }


    public synchronized void setFlags(int flags) {
        this.flags = flags;
    }


    public synchronized PackageInfo getPackageInfo() {
        return packageInfo;
    }

    @Override
    protected void onPrepare() {
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
