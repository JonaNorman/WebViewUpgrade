package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class UpgradePackageSource extends UpgradeSource {

    private final String packageName;
    private PackageInfo packageInfo;

    public UpgradePackageSource(Context context, String packageName) {
        super(context);
        this.packageName = packageName;
    }

    public synchronized PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public String getPackageName() {
        return packageName;
    }

    @Override
    protected void onPrepare(Object params) {
        try {
            int flags = params instanceof Integer ? (int) params : 0;
            packageInfo = getContext().getPackageManager().getPackageInfo(packageName, flags);
            if (packageInfo == null) {
                throw new PackageManager.NameNotFoundException("Package " + packageName + " doesn't exist");
            }
            process(1.0f);
            success();
        } catch (PackageManager.NameNotFoundException e) {
            error(e);
        }
    }
}
