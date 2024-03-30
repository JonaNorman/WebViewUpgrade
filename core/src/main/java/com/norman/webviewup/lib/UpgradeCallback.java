package com.norman.webviewup.lib;

public interface UpgradeCallback {
    void onUpgradeProcess(float percent);
    void onUpgradeComplete();

    void onUpgradeError(Throwable throwable);
}
