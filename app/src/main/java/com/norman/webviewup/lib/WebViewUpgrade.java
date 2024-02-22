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
import android.webkit.WebView;

import com.norman.webviewup.lib.download.DownloadAction;
import com.norman.webviewup.lib.download.DownloaderSink;
import com.norman.webviewup.lib.hook.PackageManagerHook;
import com.norman.webviewup.lib.hook.WebViewUpdateServiceHook;
import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.interfaces.IWebViewFactory;
import com.norman.webviewup.lib.service.interfaces.IWebViewUpdateService;
import com.norman.webviewup.lib.util.ApkUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WebViewUpgrade {

    private static final List<UpgradeCallback> UPGRADE_CALLBACK_LIST = new ArrayList<>();
    private static final String UPGRADE_DIRECTORY = "WebViewUpgrade";


    private static final int STATUS_UNINIT = 0;

    private static final int STATUS_INIT = 1;

    private static final int STATUS_RUNNING = 2;

    private static final int STATUS_FAIL = 3;

    private static final int STATUS_COMPLETE = 4;

    private static UpgradeOptions UPGRADE_OPTIONS;

    private static int UPGRADE_STATUS = STATUS_UNINIT;


    private static float UPGRADE_PROCESS;

    private static String SYSTEM_WEB_VIEW_PACKAGE_NAME;

    private static String SYSTEM_WEB_VIEW_PACKAGE_VERSION;

    private static String UPGRADE_WEB_VIEW_PACKAGE_NAME;

    private static String UPGRADE_WEB_VIEW_PACKAGE_VERSION;


    private static Throwable UPGRADE_THROWABLE;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());





    public synchronized static void init(UpgradeOptions options) {
        if (UPGRADE_STATUS != STATUS_UNINIT) {
            throw new IllegalStateException("WebViewUpgrade is already init");
        }
        if (options == null) {
            throw new NullPointerException("options is  null");
        }
        UPGRADE_OPTIONS = options;
        UPGRADE_STATUS = STATUS_INIT;
    }


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

    public synchronized static Throwable getUpgradeError(){
        return UPGRADE_THROWABLE;
    }

    public synchronized static float getUpgradeProcess(){
        return UPGRADE_PROCESS;
    }

    public synchronized static void upgrade() {
        try {
            if (UPGRADE_STATUS == STATUS_UNINIT) {
                throw new IllegalStateException("please first init");
            }
            if (UPGRADE_STATUS != STATUS_INIT) {
                return;
            }
            UPGRADE_STATUS = STATUS_RUNNING;
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
        private String apkUrl;
        private String packageName;
        private String versionName;
        private String apkPath;
        private String soLibDir;

        private String soLibInstallCompleteKey;

        private SharedPreferences sharedPreferences;

        public UPGRADE_ACTION(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                UpgradeOptions options = UPGRADE_OPTIONS;
                DownloaderSink downloaderSink = options.downloaderSink;
                context = options.context;
                apkUrl = options.url;
                packageName = options.packageName;
                versionName = options.versionName;
                apkPath = new File(context.getFilesDir(),
                        UPGRADE_DIRECTORY
                                + "/" + packageName
                                + "/" + versionName
                                + "/base.apk").getAbsolutePath();

                soLibDir = new File(context.getFilesDir(),
                        UPGRADE_DIRECTORY
                                + "/" + packageName
                                + "/" + versionName
                                + "/libs").getAbsolutePath();

                sharedPreferences = context
                        .getSharedPreferences(
                                UPGRADE_DIRECTORY,
                                Context.MODE_PRIVATE);

                soLibInstallCompleteKey = packageName + ":" + versionName;

                File soDir = new File(soLibDir);
                if (!soDir.exists()) {
                    soDir.mkdirs();
                }

                boolean installComplete = sharedPreferences
                        .getBoolean(soLibInstallCompleteKey, false);
                if (installComplete) {
                    upgradeWebView();
                } else {
                    DownloadAction downloadAction = downloaderSink.createDownload(apkUrl, apkPath);
                    if (downloadAction.isCompleted()) {
                        extractNativeLibrary();
                        upgradeWebView();
                    } else {
                        downloadAction.addCallback(new DownloadAction.Callback() {

                            @Override
                            public void onComplete(String path) {
                                handler.post(() -> {
                                    try {
                                        extractNativeLibrary();
                                        upgradeWebView();
                                    }catch (Throwable throwable){
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

        private void extractNativeLibrary() {
            callProcessCallback(0.92f);
            ApkUtils.extractNativeLibrary(apkPath, soLibDir);
            callProcessCallback(0.94f);
            sharedPreferences
                    .edit()
                    .putBoolean(soLibInstallCompleteKey, true)
                    .commit();
        }

        private void upgradeWebView() {
            callProcessCallback(0.95f);
            replaceWebViewProvider(context,
                    packageName,
                    versionName,
                    apkPath,
                    soLibDir);
            handler.getLooper().quit();
        }
    }

    private static void replaceWebViewProvider(Context context,
                                               String packageName,
                                               String versionName,
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
            if (!Objects.equals(packageInfo.packageName, packageName)) {
                throw new IllegalArgumentException("packageName:"
                        + packageInfo.packageName
                        + " in the options is different from packageName:"
                        + packageName + " in the apk");
            }
            if (!Objects.equals(packageInfo.versionName, versionName)) {
                throw new IllegalArgumentException("versionName:"
                        + packageInfo.versionName
                        + " in the options is different from versionName:"
                        + versionName + " in the apk");
            }

            int sdkVersion = Build.VERSION.SDK_INT;
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (sdkVersion < applicationInfo.minSdkVersion) {
                    throw new RuntimeException("The current system version " + sdkVersion + " is smaller than the minimum version " + applicationInfo.minSdkVersion + "required by the apk  " + apkPath);
                }
            }

            checkWebView();
            managerHook = new PackageManagerHook(context, packageName, apkPath, soLibDir);
            updateServiceHook = new WebViewUpdateServiceHook(context, packageName);
            managerHook.hook();
            callProcessCallback(0.97f);
            updateServiceHook.hook();
            callProcessCallback(0.98f);

            Object lock = new Object();
            AtomicBoolean loadOver = new AtomicBoolean();
            AtomicReference<Throwable> throwableReference = new AtomicReference<>(null);
            MAIN_HANDLER.post(() -> {
                try {
                    loadSystemWebViewPackage();
                    checkWebView();
                    new WebView(context);
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
//            if (managerHook != null) {
//                managerHook.restore();
//            }
//            if (updateServiceHook != null) {
//                updateServiceHook.restore();
//            }

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
        synchronized (WebViewUpgrade.class){
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
        if (SYSTEM_WEB_VIEW_PACKAGE_NAME != null) {
            return SYSTEM_WEB_VIEW_PACKAGE_NAME;
        }
        loadSystemWebViewPackage();
        return SYSTEM_WEB_VIEW_PACKAGE_NAME;
    }

    public synchronized static String getSystemWebViewPackageVersion() {
        if (SYSTEM_WEB_VIEW_PACKAGE_VERSION != null) {
            return SYSTEM_WEB_VIEW_PACKAGE_VERSION;
        }
        loadSystemWebViewPackage();
        return SYSTEM_WEB_VIEW_PACKAGE_VERSION;
    }

    public synchronized static String getUpgradeWebViewPackageName() {
        return UPGRADE_OPTIONS != null ? UPGRADE_OPTIONS.packageName : null;
    }

    public synchronized static String getUpgradeWebViewVersion() {
        return UPGRADE_OPTIONS != null ? UPGRADE_OPTIONS.versionName : null;
    }

    private static void loadSystemWebViewPackage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PackageInfo packageInfo = WebView.getCurrentWebViewPackage();
                SYSTEM_WEB_VIEW_PACKAGE_NAME = packageInfo.packageName;
                SYSTEM_WEB_VIEW_PACKAGE_VERSION = packageInfo.versionName;
            } catch (Throwable ignore) {

            }
        }
        if (SYSTEM_WEB_VIEW_PACKAGE_NAME == null) {
            try {
                IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);
                IBinder binder = serviceManager.getService(IWebViewUpdateService.SERVICE);
                IWebViewUpdateService service = RuntimeAccess.staticAccess(IWebViewUpdateService.class);
                IInterface iInterface = service.asInterface(binder);
                service = RuntimeAccess.objectAccess(IWebViewUpdateService.class, iInterface);
                PackageInfo packageInfo = service.getCurrentWebViewPackage();
                SYSTEM_WEB_VIEW_PACKAGE_NAME = packageInfo.packageName;
                SYSTEM_WEB_VIEW_PACKAGE_VERSION = packageInfo.versionName;
            } catch (Throwable ignore) {
            }
        }
    }

}
