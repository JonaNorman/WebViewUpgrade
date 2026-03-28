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

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

            diagIcuDataAccess(context, packageInfo.packageName, apkPath);

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


    /**
     * 诊断：在主进程中检查 WebView APK 是否包含可用的 ICU 数据文件 (assets/icudtl.dat)。
     * <p>
     * Chromium 渲染器启动时通过 setupConnection 从浏览器进程接收 ICU fd；若该 fd=-1
     * 则说明浏览器进程打开 icudtl.dat 失败，渲染子进程会直接 SIGTRAP 崩溃。
     * <ul>
     *   <li>如果日志显示「compression=DEFLATED」：APK 以压缩方式存储了 ICU 数据，
     *       AssetManager.openNonAssetFd 无法使用，需要换用未压缩打包的 APK。</li>
     *   <li>如果日志显示「ICU 文件不存在」：该 APK 可能是 Trichrome 基础包，
     *       ICU 在单独的 trichrome library APK 中，需要同时提供该 library APK。</li>
     * </ul>
     */
    private static void diagIcuDataAccess(Context context, String webViewPkg, String apkPath) {
        // 1. 直接检查 APK ZIP 结构
        if (apkPath != null) {
            try (ZipFile zf = new ZipFile(apkPath)) {
                ZipEntry icuEntry = zf.getEntry("assets/icudtl.dat");
                if (icuEntry != null) {
                    boolean uncompressed = icuEntry.getMethod() == ZipEntry.STORED;
                    Log.i(TAG, "[ICU诊断] APK 中存在 assets/icudtl.dat"
                            + " compression=" + (uncompressed ? "STORED(可用)" : "DEFLATED(不可用)")
                            + " size=" + icuEntry.getSize());
                    if (!uncompressed) {
                        Log.e(TAG, "[ICU诊断] icudtl.dat 是压缩存储，openNonAssetFd 会失败！"
                                + "请使用 -0 icudtl.dat 重新打包 APK，或换用官方未压缩版本。");
                    }
                } else {
                    Log.w(TAG, "[ICU诊断] APK 中 assets/icudtl.dat 不存在，列出 icu 相关条目：");
                    java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        if (e.getName().toLowerCase().contains("icu")) {
                            Log.w(TAG, "  [ICU候选] " + e.getName()
                                    + " method=" + e.getMethod() + " size=" + e.getSize());
                        }
                    }
                    Log.e(TAG, "[ICU诊断] 未找到 icudtl.dat，APK 可能是 Trichrome 基础包，"
                            + "需要同时提供 Trichrome Library APK。");
                }
            } catch (Exception e) {
                Log.e(TAG, "[ICU诊断] 打开 APK ZIP 失败: " + e.getMessage());
            }
        }

        // 2. 用 AssetManager 实际测试能否获取 fd（主进程 WebViewFactory 走的就是这条路）
        if (webViewPkg != null) {
            try {
                // 先清除 LoadedApk 缓存，保证 createPackageContext 用我们 hook 返回的
                // ApplicationInfo（publicSourceDir=apkPath）建新 LoadedApk，
                // 使 mResDir = apkPath 而非 null。
                invalidateLoadedApkCache(webViewPkg);
                Context wvCtx = context.createPackageContext(webViewPkg,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

                // 兜底：addAssetPath 绕过 ResourcesManager "not up to date" 检查
                try {
                    java.lang.reflect.Method addAssetPath =
                            android.content.res.AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                    addAssetPath.setAccessible(true);
                    int cookie = (int) addAssetPath.invoke(wvCtx.getAssets(), apkPath);
                    Log.i(TAG, "[ICU诊断] addAssetPath cookie=" + cookie);
                } catch (Exception e2) {
                    Log.w(TAG, "[ICU诊断] addAssetPath 失败: " + e2.getMessage());
                }

                android.content.res.AssetFileDescriptor afd =
                        wvCtx.getAssets().openNonAssetFd("assets/icudtl.dat");
                Log.i(TAG, "[ICU诊断] AssetManager.openNonAssetFd 成功"
                        + " fd=" + afd.getParcelFileDescriptor().getFd()
                        + " offset=" + afd.getStartOffset()
                        + " len=" + afd.getDeclaredLength()
                        + " → ICU fd 应能正常传给渲染进程");
                afd.close();
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                Log.e(TAG, "[ICU诊断] createPackageContext 找不到包: " + webViewPkg, e);
            } catch (Exception e) {
                Log.e(TAG, "[ICU诊断] AssetManager.openNonAssetFd 失败: " + e.getMessage()
                        + " → Chromium 无法获取 ICU fd，渲染进程将崩溃");
            }
        }
    }

    /**
     * 清除 ActivityThread 中对指定包名 LoadedApk 的缓存。
     * 详见 AbstractWebViewPackageManagerHook.invalidateLoadedApkCache 注释。
     */
    private static void invalidateLoadedApkCache(String packageName) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentAt = atClass.getMethod("currentActivityThread");
            Object at = currentAt.invoke(null);
            if (at == null) return;
            for (String fieldName : new String[]{"mPackages", "mResourcePackages"}) {
                try {
                    java.lang.reflect.Field f = atClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object map = f.get(at);
                    if (map instanceof java.util.Map) {
                        ((java.util.Map<?, ?>) map).remove(packageName);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "invalidateLoadedApkCache failed: " + e.getMessage());
        }
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


