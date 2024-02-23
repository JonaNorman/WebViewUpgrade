package com.norman.webviewup.lib;


import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebView;

import com.norman.webviewup.lib.download.DownloadAction;
import com.norman.webviewup.lib.download.DownloadSink;
import com.norman.webviewup.lib.hook.PackageManagerHook;
import com.norman.webviewup.lib.hook.WebViewUpdateServiceHook;
import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.interfaces.IWebViewFactory;
import com.norman.webviewup.lib.service.interfaces.IWebViewUpdateService;
import com.norman.webviewup.lib.util.ApkUtils;
import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.Md5Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewUpgrade {

    private static final List<UpgradeCallback> UPGRADE_CALLBACK_LIST = new ArrayList<>();
    private static final String UPGRADE_DIRECTORY = "WebViewUpgrade";


    private static final int STATUS_UNINIT = 0;

    private static final int STATUS_RUNNING = 1;

    private static final int STATUS_FAIL = 2;

    private static final int STATUS_COMPLETE = 3;

    private static UpgradeOptions UPGRADE_OPTIONS;

    private static int UPGRADE_STATUS = STATUS_UNINIT;


    private static float UPGRADE_PROCESS;

    private static PackageInfo SYSTEM_WEB_VIEW_PACKAGE_INFO;

    private static PackageInfo UPGRADE_WEB_VIEW_PACKAGE_INFO;

    private static Throwable UPGRADE_THROWABLE;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());


    public synchronized static void addUpgradeCallback(UpgradeCallback upgradeCallback) {
        if (upgradeCallback == null) return;
        if (UPGRADE_CALLBACK_LIST.contains(upgradeCallback)) return;
        UPGRADE_CALLBACK_LIST.add(upgradeCallback);
    }

    public synchronized static void removeUpgradeCallback(UpgradeCallback upgradeCallback) {
        if (upgradeCallback == null) return;
        if (!UPGRADE_CALLBACK_LIST.contains(upgradeCallback)) return;
        UPGRADE_CALLBACK_LIST.remove(upgradeCallback);
    }


    public synchronized static boolean isProcessing() {
        return UPGRADE_STATUS == STATUS_RUNNING;
    }

    public synchronized static boolean isCompleted() {
        return UPGRADE_STATUS == STATUS_COMPLETE;
    }

    public synchronized static boolean isFailed() {
        return UPGRADE_STATUS == STATUS_FAIL;
    }

    public synchronized static boolean isInited() {
        return UPGRADE_STATUS != STATUS_UNINIT;
    }

    public synchronized static Throwable getUpgradeError() {
        return UPGRADE_THROWABLE;
    }

    public synchronized static float getUpgradeProcess() {
        return UPGRADE_PROCESS;
    }

    public synchronized static void upgrade(UpgradeOptions options) {
        try {
            if (UPGRADE_STATUS == STATUS_RUNNING || UPGRADE_STATUS == STATUS_COMPLETE) {
                return;
            }
            UPGRADE_OPTIONS = options;
            UPGRADE_STATUS = STATUS_RUNNING;
            UPGRADE_PROCESS = 0;
            UPGRADE_THROWABLE = null;
            HandlerThread upgradeThread = new HandlerThread("WebViewUpgrade");
            upgradeThread.start();
            Handler upgradeHandler = new Handler(upgradeThread.getLooper());
            upgradeHandler.post(new UPGRADE_ACTION(upgradeHandler));
        } catch (Throwable throwable) {
            callErrorCallback(throwable);
        }
    }

    static class UPGRADE_ACTION implements Runnable {

        private final Handler handler;
        private Context context;

        private String apkPathKey;

        private String libsPathKey;

        private String apkPath;
        private String libsDir;


        private SharedPreferences sharedPreferences;

        public UPGRADE_ACTION(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                UpgradeOptions options = UPGRADE_OPTIONS;
                DownloadSink downloaderSink = options.downloaderSink;
                context = options.context;
                String apkUrl = options.url;
                String md5 = Md5Utils.getMd5(apkUrl);
                if (TextUtils.isEmpty(md5)) {
                    throw new RuntimeException("get url md5 is empty, url is " + apkUrl);
                }
                apkPathKey = md5 + "_apk";
                libsPathKey = md5 + "_libs";
                sharedPreferences = context
                        .getSharedPreferences(
                                UPGRADE_DIRECTORY,
                                Context.MODE_PRIVATE);
                apkPath = sharedPreferences
                        .getString(apkPathKey, null);
                libsDir = sharedPreferences
                        .getString(libsPathKey, null);

                if (!TextUtils.isEmpty(apkPath)
                        && new File(apkPath).exists() &&
                        !TextUtils.isEmpty(libsDir)
                        && new File(libsDir).exists()) {
                    upgradeWebView();
                } else {
                    String downloadPath = new File(context.getFilesDir(),
                            UPGRADE_DIRECTORY
                                    + "/tmp/" + md5 + ".download").getAbsolutePath();
                    DownloadAction downloadAction = downloaderSink.createDownload(apkUrl, downloadPath);
                    if (downloadAction.isCompleted()) {
                        installApk(downloadPath);
                        downloadAction.delete();
                        upgradeWebView();
                    } else {
                        downloadAction.addCallback(new DownloadAction.Callback() {

                            @Override
                            public void onComplete(String path) {
                                handler.post(() -> {
                                    try {
                                        installApk(path);
                                        downloadAction.delete();
                                        upgradeWebView();
                                    } catch (Throwable throwable) {
                                        callErrorCallback(throwable);
                                        handler.getLooper().quit();
                                    }
                                });
                            }

                            @Override
                            public void onFail(Throwable throwable) {
                                callErrorCallback(throwable);
                                handler.getLooper().quit();
                            }

                            @Override
                            public void onProcess(float percent) {
                                callProcessCallback(percent * 0.90f);
                            }
                        });
                    }
                    downloadAction.start();
                }
            } catch (Throwable throwable) {
                callErrorCallback(throwable);
                handler.getLooper().quit();
            }
        }


        private void upgradeWebView() {
            callProcessCallback(0.95f);
            replaceWebViewProvider(context,
                    apkPath,
                    libsDir);
            handler.getLooper().quit();
        }

        private void installApk(String downloadFilePath) {
            File downloadFile = new File(downloadFilePath);
            File downloadDirectory = downloadFile.getParentFile();
            String downloadName = downloadFile.getName();
            int dotIndex = downloadName.indexOf(".");
            String tempApkPath;
            if (dotIndex >= 0) {
                tempApkPath = new File(downloadDirectory, downloadName.substring(0, dotIndex) + ".apk").getAbsolutePath();
            } else {
                tempApkPath = new File(downloadDirectory, downloadName + ".apk").getAbsolutePath();
            }
            PackageInfo packageInfo = ApkUtils.extractApk(context, downloadFilePath, tempApkPath);
            callProcessCallback(0.92f);



            apkPath = new File(context.getFilesDir(),
                    UPGRADE_DIRECTORY
                            + "/" + packageInfo.packageName + "/" + packageInfo.versionName + ".apk").getAbsolutePath();
            libsDir = new File(context.getFilesDir(),
                    UPGRADE_DIRECTORY
                            + "/" + packageInfo.packageName + "/" + packageInfo.versionName + "/libs").getAbsolutePath();

            FileUtils.moveFile(new File(tempApkPath),new File(apkPath));
            ApkUtils.extractNativeLibrary(apkPath, libsDir);
            callProcessCallback(0.94f);
            sharedPreferences
                    .edit()
                    .putString(apkPathKey, apkPath)
                    .commit();
            sharedPreferences
                    .edit()
                    .putString(libsPathKey, libsDir)
                    .commit();
        }
    }


    private static void replaceWebViewProvider(Context context,
                                               String apkPath,
                                               String soLibDir) {
        PackageManagerHook managerHook = null;
        WebViewUpdateServiceHook updateServiceHook = null;
        try {
            callProcessCallback(0.96f);
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0);

            if (packageInfo == null) {
                throw new NullPointerException("path: " + apkPath + " is not apk");
            }

            int sdkVersion = Build.VERSION.SDK_INT;
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (sdkVersion < applicationInfo.minSdkVersion) {
                    throw new RuntimeException("The current system version " + sdkVersion + " is smaller than the minimum version " + applicationInfo.minSdkVersion + "required by the apk  " + apkPath);
                }
            }
            managerHook = new PackageManagerHook(context, packageInfo.packageName, apkPath, soLibDir);
            updateServiceHook = new WebViewUpdateServiceHook(context, packageInfo.packageName);
            managerHook.hook();
            callProcessCallback(0.97f);
            updateServiceHook.hook();
            callProcessCallback(0.98f);

            Object lock = new Object();
            AtomicBoolean loadOver = new AtomicBoolean();
            AtomicReference<Throwable> throwableReference = new AtomicReference<>(null);
            MAIN_HANDLER.post(() -> {
                try {
                    synchronized (WebViewUpgrade.class) {
                        if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
                            SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
                        }
                    }
                    checkWebView();
                    new WebView(context);
                    synchronized (WebViewUpgrade.class) {
                        UPGRADE_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
                    }
                } catch (Throwable throwable) {
                    throwableReference.set(throwable);
                } finally {
                    synchronized (lock) {
                        loadOver.set(true);
                        lock.notifyAll();
                    }
                }
            });
            synchronized (lock) {
                long startTime = System.currentTimeMillis();
                while (!loadOver.get()) {
                    try {
                        lock.wait(100);
                    } catch (InterruptedException ignore) {
                    }
                    if ((System.currentTimeMillis() - startTime) > 5000) {
                        throwableReference.set(new RuntimeException("webView load timeOut"));
                        break;
                    }
                }
            }
            Throwable throwable = throwableReference.get();
            if (throwable != null) {
                throw new RuntimeException(throwable);
            }
            callProcessCallback(1.0f);
            callCompleteCallback();
        } finally {
            if (managerHook != null) {
                managerHook.restore();
            }
            if (updateServiceHook != null) {
                updateServiceHook.restore();
            }

        }
    }

    private static void checkWebView() {
        IWebViewFactory webViewFactory = RuntimeAccess.staticAccess(IWebViewFactory.class);
        Object providerInstance = webViewFactory.getProviderInstance();
        if (providerInstance != null) {
            throw new IllegalStateException("WebViewProvider has been created, and the upgrade function can only be used before the webview is instantiated");
        }
    }


    private static void callErrorCallback(Throwable throwable) {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_STATUS = STATUS_FAIL;
            UPGRADE_THROWABLE = throwable;
        }
        runInMainThread(() -> {
            synchronized (WebViewUpgrade.class) {
                for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_LIST) {
                    upgradeCallback.onUpgradeError(throwable);
                }
            }
        });
    }

    private static void callProcessCallback(float percent) {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_PROCESS = percent;
        }
        runInMainThread(() -> {
            synchronized (WebViewUpgrade.class) {
                for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_LIST) {
                    upgradeCallback.onUpgradeProcess(percent);
                }
            }
        });
    }

    private static void callCompleteCallback() {
        synchronized (WebViewUpgrade.class) {
            UPGRADE_STATUS = STATUS_COMPLETE;
        }
        runInMainThread(() -> {
            synchronized (WebViewUpgrade.class) {
                for (UpgradeCallback upgradeCallback : UPGRADE_CALLBACK_LIST) {
                    upgradeCallback.onUpgradeComplete();
                }
            }
        });
    }


    private static void runInMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }

    public synchronized static String getSystemWebViewPackageName() {
        if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
            SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
        }
        return SYSTEM_WEB_VIEW_PACKAGE_INFO != null ? SYSTEM_WEB_VIEW_PACKAGE_INFO.packageName : null;
    }

    public synchronized static String getSystemWebViewPackageVersion() {
        if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
            SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
        }
        return SYSTEM_WEB_VIEW_PACKAGE_INFO != null ? SYSTEM_WEB_VIEW_PACKAGE_INFO.versionName : null;
    }

    public synchronized static String getUpgradeWebViewPackageName() {
        return UPGRADE_WEB_VIEW_PACKAGE_INFO != null ? UPGRADE_WEB_VIEW_PACKAGE_INFO.packageName : null;
    }

    public synchronized static String getUpgradeWebViewVersion() {
        return UPGRADE_WEB_VIEW_PACKAGE_INFO != null ? UPGRADE_WEB_VIEW_PACKAGE_INFO.versionName : null;
    }

    private static PackageInfo loadCurrentWebViewPackageInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return WebView.getCurrentWebViewPackage();
            } catch (Throwable ignore) {

            }
        }
        try {
            IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);
            IBinder binder = serviceManager.getService(IWebViewUpdateService.SERVICE);
            IWebViewUpdateService service = RuntimeAccess.staticAccess(IWebViewUpdateService.class);
            IInterface iInterface = service.asInterface(binder);
            service = RuntimeAccess.objectAccess(IWebViewUpdateService.class, iInterface);
            PackageInfo packageInfo = service.getCurrentWebViewPackage();
            return packageInfo;
        } catch (Throwable ignore) {
        }
        return null;
    }

}
