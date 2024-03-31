package com.norman.webviewup.lib;


import com.norman.webviewup.lib.source.UpgradePackageSource;
import com.norman.webviewup.lib.source.UpgradePathSource;
import com.norman.webviewup.lib.source.UpgradeSource;
import com.norman.webviewup.lib.util.HandlerUtils;

import java.util.HashSet;
import java.util.Set;

public class WebViewUpgrade {

    private static final Set<UpgradeCallback> UPGRADE_CALLBACK_SET = new HashSet<>();

    private static final int STATUS_NEW = 0;

    private static final int STATUS_RUNNING = 1;

    private static final int STATUS_FAIL = 2;

    private static final int STATUS_COMPLETE = 3;

    private static int UPGRADE_STATUS = STATUS_NEW;

    private static float UPGRADE_PROCESS;


    private static Throwable UPGRADE_THROWABLE;

    public synchronized static boolean isProcessing() {
        return UPGRADE_STATUS == STATUS_RUNNING;
    }

    public synchronized static boolean isCompleted() {
        return UPGRADE_STATUS == STATUS_COMPLETE;
    }

    public synchronized static boolean isFailed() {
        return UPGRADE_STATUS == STATUS_FAIL;
    }

    public synchronized static Throwable getUpgradeError() {
        return UPGRADE_THROWABLE;
    }

    public synchronized static float getUpgradeProcess() {
        return UPGRADE_PROCESS;
    }

    public synchronized static void addUpgradeCallback(UpgradeCallback upgradeCallback) {
        UPGRADE_CALLBACK_SET.add(upgradeCallback);
    }

    public synchronized static void removeUpgradeCallback(UpgradeCallback upgradeCallback) {
        UPGRADE_CALLBACK_SET.remove(upgradeCallback);
    }

    public static String getSystemWebViewPackageName() {
        return WebViewReplace.getSystemWebViewPackageName();
    }

    public static String getSystemWebViewPackageVersion() {
        return WebViewReplace.getSystemWebViewPackageVersion();
    }

    public static String getUpgradeWebViewPackageName() {
        return WebViewReplace.getReplaceWebViewPackageName();
    }

    public static String getUpgradeWebViewVersion() {
        return WebViewReplace.getReplaceWebViewVersion();
    }


    public synchronized static void upgrade(UpgradeSource webViewSource) {
        try {
            if (UPGRADE_STATUS == STATUS_RUNNING || UPGRADE_STATUS == STATUS_COMPLETE) {
                return;
            }
            UPGRADE_STATUS = STATUS_RUNNING;
            UPGRADE_THROWABLE = null;
            UPGRADE_PROCESS = 0;

            webViewSource.prepare(new UpgradeSource.OnPrepareCallback() {
                @Override
                public void onPrepareSuccess(UpgradeSource webViewSource) {
                    HandlerUtils.runInMainThread(() -> {
                        try {
                            if (webViewSource instanceof UpgradePathSource) {
                                UpgradePathSource upgradePathSource = (UpgradePathSource) webViewSource;
                                WebViewReplace.replace(webViewSource.getContext(),
                                        upgradePathSource.getApkPath(),
                                        upgradePathSource.getLibsPath());
                            } else if (webViewSource instanceof UpgradePackageSource) {
                                UpgradePackageSource upgradePackageSource = (UpgradePackageSource) webViewSource;
                                WebViewReplace.replace(webViewSource.getContext(),
                                        upgradePackageSource.getPackageInfo());
                            }
                            callProcessCallback(1.0f);
                            callCompleteCallback();
                        } catch (WebViewReplaceException e) {
                            callErrorCallback(e);
                        }
                    });
                }

                @Override
                public void onPrepareProcess(UpgradeSource webViewSource, float percent) {
                    callProcessCallback(percent * 0.99f);
                }

                @Override
                public void onPrepareError(UpgradeSource webViewSource, Throwable throwable) {
                    callErrorCallback(throwable);
                }
            });

        } catch (Throwable throwable) {
            callErrorCallback(throwable);
        }
    }


    private static void callErrorCallback(Throwable throwable) {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_STATUS = STATUS_FAIL;
            UPGRADE_THROWABLE = throwable;
            for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_SET) {
                if (upgradeCallback == null) continue;
                HandlerUtils.runInMainThread(() -> upgradeCallback.onUpgradeError(throwable));
            }
        }
    }

    private static void callCompleteCallback() {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_STATUS = STATUS_COMPLETE;
            for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_SET) {
                if (upgradeCallback == null) continue;
                HandlerUtils.runInMainThread(upgradeCallback::onUpgradeComplete);
            }
        }
    }

    private static void callProcessCallback(float percent) {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_PROCESS = percent;
            for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_SET) {
                if (upgradeCallback == null) continue;
                HandlerUtils.runInMainThread(() -> upgradeCallback.onUpgradeProcess(percent));
            }
        }
    }


}
