package com.norman.webviewup.lib;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebView;

import com.norman.webviewup.lib.hook.PackageManagerServiceHook;
import com.norman.webviewup.lib.hook.WebViewUpdateServiceHook;
import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.interfaces.IWebViewFactory;
import com.norman.webviewup.lib.service.interfaces.IWebViewUpdateService;
import com.norman.webviewup.lib.util.FileUtils;


public class WebViewReplace {

    private static PackageInfo SYSTEM_WEB_VIEW_PACKAGE_INFO;

    private static PackageInfo REPLACE_WEB_VIEW_PACKAGE_INFO;

    public synchronized static void replace(Context context, String apkPath,String libsPath) throws WebViewReplaceException {
        PackageManagerServiceHook managerHook = null;
        WebViewUpdateServiceHook updateServiceHook = null;
        try {
            if (context == null) {
                throw new WebViewReplaceException("context is null");
            }
            if (!FileUtils.existFile(apkPath)) {
                throw new WebViewReplaceException("apkFile is not exist");
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new WebViewReplaceException("replace webView only in main thread");
            }
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageArchiveInfo(apkPath, 0);

            if (packageInfo == null) {
                throw new WebViewReplaceException(apkPath + " is not apk");
            }

            int sdkVersion = Build.VERSION.SDK_INT;
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (sdkVersion < applicationInfo.minSdkVersion) {
                    throw new WebViewReplaceException("current system version " + sdkVersion + " is smaller than the minimum version " + applicationInfo.minSdkVersion + " required by the apk  " + apkPath);
                }
            }
            managerHook = new PackageManagerServiceHook(context, packageInfo.packageName, apkPath,libsPath);
            updateServiceHook = new WebViewUpdateServiceHook(context, packageInfo.packageName);
            managerHook.hook();
            updateServiceHook.hook();
            if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
                SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
            }
            checkWebView(context);
            REPLACE_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
        } catch (Throwable throwable) {
            if (throwable instanceof WebViewReplaceException) {
                throw throwable;
            } else {
                String message = throwable.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = "";
                }
                throw new WebViewReplaceException(message, throwable);
            }
        } finally {
            try {
                if (managerHook != null) {
                    managerHook.restore();
                }
                if (updateServiceHook != null) {
                    updateServiceHook.restore();
                }
            } catch (Throwable throwable) {
                String message = throwable.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = "";
                }
                throw new WebViewReplaceException(message, throwable);
            }
        }
    }


    public synchronized static void replace(Context context, PackageInfo packageInfo) throws WebViewReplaceException {
        WebViewUpdateServiceHook updateServiceHook = null;
        try {
            if (context == null) {
                throw new WebViewReplaceException("context is null");
            }
            if (packageInfo == null) {
                throw new WebViewReplaceException("packageInfo is null");
            }

            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new WebViewReplaceException("replace webView only in main thread");
            }

            updateServiceHook = new WebViewUpdateServiceHook(context, packageInfo.packageName);
            updateServiceHook.hook();
            if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
                SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
            }
            checkWebView(context);
            REPLACE_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
        } catch (Throwable throwable) {
            if (throwable instanceof WebViewReplaceException) {
                throw throwable;
            } else {
                String message = throwable.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = "";
                }
                throw new WebViewReplaceException(message, throwable);
            }
        } finally {
            try {
                if (updateServiceHook != null) {
                    updateServiceHook.restore();
                }
            } catch (Throwable throwable) {
                String message = throwable.getMessage();
                if (TextUtils.isEmpty(message)) {
                    message = "";
                }
                throw new WebViewReplaceException(message, throwable);
            }
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

    public synchronized static String getReplaceWebViewPackageName() {
        return REPLACE_WEB_VIEW_PACKAGE_INFO != null ? REPLACE_WEB_VIEW_PACKAGE_INFO.packageName : null;
    }

    public synchronized static String getReplaceWebViewVersion() {
        return REPLACE_WEB_VIEW_PACKAGE_INFO != null ? REPLACE_WEB_VIEW_PACKAGE_INFO.versionName : null;
    }


    private static void checkWebView(Context context) throws WebViewReplaceException {
        IWebViewFactory webViewFactory = RuntimeAccess.staticAccess(IWebViewFactory.class);
        Object providerInstance = webViewFactory.getProviderInstance();
        if (providerInstance != null) {
            throw new WebViewReplaceException("WebView only can replace before System WebView init");
        }
        new WebView(context);
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
            return service.getCurrentWebViewPackage();
        } catch (Throwable ignore) {
        }
        return null;
    }


}
