package com.norman.webviewup.lib;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;

import com.norman.webviewup.lib.hook.PackageManagerServiceHook;
import com.norman.webviewup.lib.hook.WebViewUpdateServiceHook;
import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.interfaces.IWebViewFactory;
import com.norman.webviewup.lib.service.interfaces.IWebViewUpdateService;
import com.norman.webviewup.lib.util.FileUtils;

import com.norman.webviewup.lib.hook.ActivityManagerHook;

public class WebViewReplace {

    private static final String TAG = "WebViewReplace";

    private static PackageInfo SYSTEM_WEB_VIEW_PACKAGE_INFO;

    public static PackageInfo REPLACE_WEB_VIEW_PACKAGE_INFO;

    /** 持久的拦截沙盒进程 Hook，整个应用生命周期中只有一个实例 */
    private static ActivityManagerHook sActivityManagerHook;
    /** 持久的 PMS Hook，多进程模式下 Chromium 可能在运行时查询 WebView 包组件信息 */
    private static PackageManagerServiceHook sManagerHook;

    public synchronized static void replace(Context context, String apkPath,String libsPath) throws WebViewReplaceException {
        try {
            if (context == null) {
                throw new WebViewReplaceException("context is null");
            }
            if (!FileUtils.existFile(apkPath)) {
                throw new WebViewReplaceException("apkFile is not exist");
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
            
            replaceInternal(context, packageInfo, apkPath, libsPath);
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
        }
    }

    public synchronized static void replace(Context context, PackageInfo packageInfo) throws WebViewReplaceException {
        try {
            if (context == null) {
                throw new WebViewReplaceException("context is null");
            }
            if (packageInfo == null) {
                throw new WebViewReplaceException("packageInfo is null");
            }
            String sourceDir = packageInfo.applicationInfo != null
                    ? packageInfo.applicationInfo.sourceDir : null;
            replaceInternal(context, packageInfo, sourceDir, null);
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
        }
    }

    private synchronized static void replaceInternal(Context context, PackageInfo packageInfo, String apkPath, String libsPath) throws WebViewReplaceException {
        IWebViewFactory webViewFactory = RuntimeAccess.staticAccess(IWebViewFactory.class);
        Object providerInstance = webViewFactory.getProviderInstance();
        if (providerInstance != null) {
            throw new WebViewReplaceException("WebView can only be replaced before System WebView init");
        }

        WebViewUpdateServiceHook updateServiceHook = null;
        boolean replaceSuccess = false;
        try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new WebViewReplaceException("replace webView only in main thread");
            }

            if (apkPath != null && libsPath != null) {
                if (sManagerHook == null) {
                    sManagerHook = new PackageManagerServiceHook(context, packageInfo.packageName, apkPath, libsPath);
                }
            }

            updateServiceHook = new WebViewUpdateServiceHook(context, packageInfo.packageName);

            if (sActivityManagerHook == null) {
                sActivityManagerHook = new ActivityManagerHook(context, packageInfo.packageName, apkPath, libsPath);
            } else {
                sActivityManagerHook.update(packageInfo.packageName, apkPath, libsPath);
            }

            if (sManagerHook != null) {
                sManagerHook.hook();
            }
            updateServiceHook.hook();
            sActivityManagerHook.hook();

            if (SYSTEM_WEB_VIEW_PACKAGE_INFO == null) {
                SYSTEM_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
            }
            
            checkWebView(context);
            REPLACE_WEB_VIEW_PACKAGE_INFO = loadCurrentWebViewPackageInfo();
            replaceSuccess = true;
        } finally {
            try {
                if (updateServiceHook != null) {
                    updateServiceHook.restore();
                }
                // 替换失败时释放所有持久 Hook，防止残留
                if (!replaceSuccess) {
                    if (sManagerHook != null) {
                        sManagerHook.restore();
                        sManagerHook = null;
                    }
                    if (sActivityManagerHook != null) {
                        sActivityManagerHook.restore();
                        sActivityManagerHook = null;
                    }
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
        WebView webView = new WebView(context);
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Log.w("WebViewReplace", "checkWebView 的 WebView 渲染进程已退出, didCrash=" + detail.didCrash());
                return true;
            }
        });
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


