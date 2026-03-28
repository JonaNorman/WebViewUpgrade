package com.norman.webviewup.lib.sandbox;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.norman.webviewup.lib.hook.SandboxedProcessPackageManagerHook;
import com.norman.webviewup.lib.util.ProcessUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * 沙盒渲染进程的委托核心。
 * <p>
 * Stub 服务运行在宿主 App 的普通（非 isolated）子进程中，因此可以直接读取宿主
 * 私有目录下的 WebView APK 文件，无需 ashmem / FD 传递等机制。
 * <p>
 * 在子进程安装 {@link SandboxedProcessPackageManagerHook} 后：
 * <ol>
 *   <li>若 {@link Context#createPackageContext(String, int)} 成功：安装 {@link ChromiumDelegatingClassLoader}
 *       替换 LoadedApk / ContextImpl 的 {@code mClassLoader}，{@code org.chromium.*} 走 WebView 包 CL
 *      （与 {@code System.loadLibrary} 的 clns 一致，避免 ICU FD 无效），其余仍走宿主 CL；Binder 反序列化
 *       与 Stub 使用同一委托 CL，避免 {@code FileDescriptorInfo} 双加载。</li>
 *   <li>该路径下不再把主 WebView APK 的 dex/native 并进宿主 PathClassLoader；可选仅合并 trichrome
 *       {@code sharedLibs}。</li>
 *   <li>createPackageContext 失败时回退：将 WebView dex 合并进宿主 CL 并 attach 宿主 Context。</li>
 * </ol>
 */
public class SandboxedProcessServiceDelegate {

    private static final String TAG = "SandboxStubDelegate";

    /** Chromium 绑定 SandboxedProcessService 时携带的浏览器包名。 */
    private static final String CHROMIUM_EXTRA_BROWSER_PACKAGE =
            "org.chromium.base.process_launcher.extra.browser_package_name";

    public static final String EXTRA_WEBVIEW_APK_PATH = "EXTRA_WEBVIEW_APK_PATH";
    public static final String EXTRA_WEBVIEW_LIBS_PATH = "EXTRA_WEBVIEW_LIBS_PATH";
    public static final String EXTRA_WEBVIEW_PACKAGE_NAME = "EXTRA_WEBVIEW_PACKAGE_NAME";
    public static final String EXTRA_ORIGINAL_SERVICE_CLASS = "EXTRA_ORIGINAL_SERVICE_CLASS";
    public static final String EXTRA_SHARED_LIBS = "EXTRA_WEBVIEW_SHARED_LIBS";

    private static final Object PMS_INSTALL_LOCK = new Object();
    private static volatile SandboxedProcessPackageManagerHook sSandboxPmsHook;

    private Service mHostService;
    private Context mAppContext;

    private Service mRealService;
    private Method mOnBindMethod;
    private Method mOnDestroyMethod;
    private Method mOnRebindMethod;

    private boolean mDexInjected = false;
    private boolean mReady = false;
    private String mWebViewApkPath = null;

    public void onCreate(Service hostService, Context appContext) {
        mHostService = hostService;
        mAppContext = appContext;
    }

    public IBinder onBind(Intent intent) {
        ensureReady(intent);
        if (!mReady || mRealService == null || mOnBindMethod == null) {
            Log.e(TAG, "onBind: Chromium 沙盒服务未就绪，返回 null");
            return null;
        }
        try {
            return (IBinder) mOnBindMethod.invoke(mRealService, intent);
        } catch (Throwable t) {
            Log.e(TAG, "onBind 委托失败", t);
            return null;
        }
    }

    public void onDestroy() {
        if (!mReady || mRealService == null) return;
        try {
            if (mOnDestroyMethod != null) {
                mOnDestroyMethod.invoke(mRealService);
            }
        } catch (Throwable t) {
            Log.e(TAG, "onDestroy 委托失败", t);
        }
    }

    public void onRebind(Intent intent) {
        if (!mReady || mRealService == null) return;
        try {
            if (mOnRebindMethod != null) {
                mOnRebindMethod.invoke(mRealService, intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "onRebind 委托失败", t);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void installSandboxPmsHookOnce(Context appContext,
                                                  String webViewPkg,
                                                  String apkPath,
                                                  String libsPath) {
        synchronized (PMS_INSTALL_LOCK) {
            if (sSandboxPmsHook != null && sSandboxPmsHook.isHook()) {
                return;
            }
            SandboxedProcessPackageManagerHook hook =
                    new SandboxedProcessPackageManagerHook(appContext, webViewPkg, apkPath, libsPath);
            hook.hook();
            sSandboxPmsHook = hook;
            Log.i(TAG, "沙盒子进程 PMS Hook 已安装, webViewPkg=" + webViewPkg);
        }
    }

    private Context tryCreateWebViewPackageContext(String webViewPkg) {
        // 先清除 LoadedApk 缓存，保证 createPackageContext 用我们 hook 返回的
        // ApplicationInfo（publicSourceDir=apkPath）重建 LoadedApk，
        // 使 ResourcesKey.mResDir = apkPath 而不是 null。
        invalidateLoadedApkCache(webViewPkg);
        try {
            Context c = mAppContext.createPackageContext(webViewPkg,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Log.i(TAG, "createPackageContext 成功: " + webViewPkg
                    + ", cl=" + c.getClassLoader().getClass().getName());
            return c;
        } catch (Throwable t) {
            Log.e(TAG, "createPackageContext 失败: " + webViewPkg, t);
            return null;
        }
    }

    /**
     * 清除 ActivityThread 中对指定包名 LoadedApk 的缓存。
     * 详见 AbstractWebViewPackageManagerHook.invalidateLoadedApkCache 注释。
     */
    private static void invalidateLoadedApkCache(String packageName) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAt = atClass.getMethod("currentActivityThread");
            Object at = currentAt.invoke(null);
            if (at == null) return;
            for (String fieldName : new String[]{"mPackages", "mResourcePackages"}) {
                try {
                    Field f = atClass.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object map = f.get(at);
                    if (map instanceof Map) {
                        ((Map<?, ?>) map).remove(packageName);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "invalidateLoadedApkCache failed: " + e.getMessage());
        }
    }

    private synchronized void ensureReady(Intent intent) {
        if (mReady) return;

        String apkPath = intent.getStringExtra(EXTRA_WEBVIEW_APK_PATH);
        String libsPath = intent.getStringExtra(EXTRA_WEBVIEW_LIBS_PATH);
        String[] sharedLibs = intent.getStringArrayExtra(EXTRA_SHARED_LIBS);
        String realClassName = intent.getStringExtra(EXTRA_ORIGINAL_SERVICE_CLASS);

        if (apkPath == null) {
            Log.e(TAG, "Intent 中缺少 APK 路径");
            return;
        }
        mWebViewApkPath = apkPath;

        if (realClassName == null || realClassName.isEmpty()) {
            realClassName = "org.chromium.content.app.SandboxedProcessService0";
        }

        String webViewPkg = intent.getStringExtra(EXTRA_WEBVIEW_PACKAGE_NAME);
        if (TextUtils.isEmpty(webViewPkg)) {
            webViewPkg = intent.getStringExtra(CHROMIUM_EXTRA_BROWSER_PACKAGE);
        }
        if (TextUtils.isEmpty(webViewPkg)) {
            Log.e(TAG, "Intent 缺少 WebView 包名，无法安装子进程 PMS Hook");
            return;
        }

        String nativeLibDir = resolveNativeLibraryDir(libsPath);

        installSandboxPmsHookOnce(mAppContext, webViewPkg, apkPath, libsPath);

        patchApplicationInfo(apkPath, nativeLibDir);

        Context webViewCtx = tryCreateWebViewPackageContext(webViewPkg);

        if (webViewCtx != null
                && installChromiumDelegatingClassLoader(webViewCtx.getClassLoader())) {
            if (!mDexInjected) {
                mDexInjected = injectDex(apkPath, nativeLibDir, sharedLibs, false);
            }
            if (!mDexInjected) {
                Log.e(TAG, "dex 注入失败（委托 CL 路径，可能缺 sharedLibs 合并）");
                return;
            }
            Context attachCtx = new SandboxedWebViewServiceContext(webViewCtx, mAppContext);
            mReady = initRealService(realClassName, attachCtx, false);
            if (mReady) {
                return;
            }
            Log.w(TAG, "委托 CL + createPackageContext 路径初始化失败，尝试宿主 CL loadClass");
            mReady = initRealService(realClassName, attachCtx, true);
            if (mReady) {
                return;
            }
            Log.w(TAG, "createPackageContext 路径彻底失败，回退仅宿主 Context");
        } else if (webViewCtx == null) {
            Log.w(TAG, "无 WebView 包 Context，使用全量 dex 合并");
        } else {
            Log.w(TAG, "ChromiumDelegatingClassLoader 安装失败，使用全量 dex 合并");
        }

        if (!mDexInjected) {
            mDexInjected = injectDex(apkPath, nativeLibDir, sharedLibs, true);
        }
        if (!mDexInjected) {
            Log.e(TAG, "dex 注入失败");
            return;
        }

        if (webViewCtx != null) {
            Context attachCtx = new SandboxedWebViewServiceContext(webViewCtx, mAppContext);
            mReady = initRealService(realClassName, attachCtx, true);
            if (mReady) {
                return;
            }
            Log.w(TAG, "全量合并后 createPackageContext 路径仍失败，回退仅宿主 Context");
        }

        mReady = initRealService(realClassName, mHostService.getBaseContext(), false);
    }

    /**
     * libsPath 与主进程 PackageManagerServiceHook 一致：解压 so 的根目录，其下为 arm64-v8a 等 ABI 子目录。
     * ApplicationInfo.nativeLibraryDir 必须是含 libwebviewchromium.so 的 ABI 目录，不能是根目录。
     */
    private String resolveNativeLibraryDir(String libsRoot) {
        if (libsRoot == null) return null;
        File dir = new File(libsRoot);
        if (!dir.isDirectory()) {
            return libsRoot;
        }
        String[] names = dir.list();
        if (names != null) {
            for (String n : names) {
                if (n != null && n.endsWith(".so")) {
                    return dir.getAbsolutePath();
                }
            }
        }
        boolean is64 = ProcessUtils.is64Bit();
        String[] abis = is64 ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        Arrays.sort(abis);
        if (names != null) {
            for (String name : names) {
                if (name != null && Arrays.binarySearch(abis, name) >= 0) {
                    return new File(dir, name).getAbsolutePath();
                }
            }
        }
        Log.w(TAG, "resolveNativeLibraryDir: 未在 " + libsRoot + " 下找到 ABI 子目录，沿用根路径");
        return dir.getAbsolutePath();
    }

    private void patchApplicationInfo(String apkPath, String nativeLibraryDir) {
        try {
            ApplicationInfo appInfo = mAppContext.getApplicationInfo();
            appInfo.sourceDir = apkPath;
            appInfo.publicSourceDir = apkPath;
            if (nativeLibraryDir != null) {
                appInfo.nativeLibraryDir = nativeLibraryDir;
            }
            Log.i(TAG, "已修改进程 ApplicationInfo: sourceDir=" + apkPath
                    + ", nativeLibraryDir=" + nativeLibraryDir);
        } catch (Throwable t) {
            Log.e(TAG, "修改 ApplicationInfo 失败", t);
        }
    }

    /**
     * 用 {@link ChromiumDelegatingClassLoader} 替换宿主 LoadedApk 与关键 ContextImpl 的
     * {@code mClassLoader}，使 Binder 默认反序列化与 Chromium 代码共用同一套 {@code org.chromium.*} 定义，
     * 且这些类的 defining loader 为 WebView 包 CL（{@code System.loadLibrary} → 正确 clns，ICU 等可 mmap APK 内资源）。
     */
    private boolean installChromiumDelegatingClassLoader(ClassLoader webViewClassLoader) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object at = atClass.getMethod("currentActivityThread").invoke(null);
            Field mPackagesField = atClass.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, WeakReference<?>> mPackages =
                    (Map<String, WeakReference<?>>) mPackagesField.get(at);
            String pkg = mAppContext.getPackageName();
            WeakReference<?> ref = mPackages != null ? mPackages.get(pkg) : null;
            if (ref == null) {
                Log.e(TAG, "installChromiumDelegatingClassLoader: mPackages 无宿主项 pkg=" + pkg);
                return false;
            }
            Object loadedApk = ref.get();
            if (loadedApk == null) {
                Log.e(TAG, "installChromiumDelegatingClassLoader: LoadedApk 引用为空");
                return false;
            }
            Class<?> loadedApkClass = Class.forName("android.app.LoadedApk");
            Field mClassLoaderField = loadedApkClass.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            ClassLoader appCl = (ClassLoader) mClassLoaderField.get(loadedApk);
            if (appCl == null) {
                Method getCl = loadedApkClass.getDeclaredMethod("getClassLoader");
                getCl.setAccessible(true);
                appCl = (ClassLoader) getCl.invoke(loadedApk);
            }
            if (appCl instanceof ChromiumDelegatingClassLoader) {
                Log.i(TAG, "ChromiumDelegatingClassLoader 已存在，跳过重复安装");
                return true;
            }
            ChromiumDelegatingClassLoader composite =
                    new ChromiumDelegatingClassLoader(appCl, webViewClassLoader);
            mClassLoaderField.set(loadedApk, composite);
            patchContextImplClassLoaderField(mAppContext, composite);
            patchContextImplClassLoaderField(mHostService, composite);
            Log.i(TAG, "ChromiumDelegatingClassLoader 安装成功");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "installChromiumDelegatingClassLoader 失败", t);
            return false;
        }
    }

    private static void patchContextImplClassLoaderField(Context ctx, ClassLoader cl)
            throws Exception {
        if (ctx == null) return;
        Context base = ctx;
        while (base instanceof ContextWrapper) {
            base = ((ContextWrapper) base).getBaseContext();
        }
        Class<?> implClass = Class.forName("android.app.ContextImpl");
        if (!implClass.isInstance(base)) {
            return;
        }
        Field f = implClass.getDeclaredField("mClassLoader");
        f.setAccessible(true);
        f.set(base, cl);
    }

    /**
     * {@code org.chromium.*} 委托给 WebView 包 ClassLoader，其余委托给原宿主 CL。
     */
    private static final class ChromiumDelegatingClassLoader extends ClassLoader {

        private final ClassLoader mAppClassLoader;
        private final ClassLoader mChromiumClassLoader;

        ChromiumDelegatingClassLoader(ClassLoader appClassLoader, ClassLoader chromiumClassLoader) {
            super(appClassLoader != null ? appClassLoader.getParent() : null);
            mAppClassLoader = appClassLoader;
            mChromiumClassLoader = chromiumClassLoader;
        }

        private static boolean isChromiumPackage(String name) {
            return name != null && name.startsWith("org.chromium.");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    if (isChromiumPackage(name)) {
                        c = mChromiumClassLoader.loadClass(name);
                    } else {
                        c = mAppClassLoader.loadClass(name);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }

    /**
     * 将 WebView / sharedLibs 的 dex（及可选 native 路径元素）并入宿主 PathClassLoader。
     *
     * @param mergeWebViewApkIntoHost false 时跳过主 WebView APK，仅处理 {@code sharedLibs}
     *                                （委托 ClassLoader 已负责 {@code org.chromium.*} 时避免双份 dex/native）。
     */
    private boolean injectDex(String apkPath, String nativeLibDir, String[] sharedLibs,
                              boolean mergeWebViewApkIntoHost) {
        try {
            if (!mergeWebViewApkIntoHost
                    && (sharedLibs == null || sharedLibs.length == 0)) {
                Log.i(TAG, "injectDex: 不合并主 WebView APK，且无 sharedLibs，跳过 PathList 修改");
                return true;
            }

            ClassLoader currentCl = mAppContext.getClassLoader();
            Class<?> dexPathListClass = Class.forName("dalvik.system.DexPathList");

            Field dexElementsField = dexPathListClass.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);

            Field nativeLibPathElementsField = null;
            try {
                nativeLibPathElementsField = dexPathListClass.getDeclaredField("nativeLibraryPathElements");
                nativeLibPathElementsField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
                Log.w(TAG, "nativeLibraryPathElements 字段不存在，跳过 native 路径注入");
            }

            Class<?> baseDexClClass = Class.forName("dalvik.system.BaseDexClassLoader");
            Field pathListField = baseDexClClass.getDeclaredField("pathList");
            pathListField.setAccessible(true);

            Object[] webviewDexElements = new Object[0];
            Object[] webviewNativeElements = new Object[0];
            if (mergeWebViewApkIntoHost) {
                dalvik.system.PathClassLoader webviewLoader = new dalvik.system.PathClassLoader(
                        apkPath, nativeLibDir, currentCl.getParent());
                Object webviewPathList = pathListField.get(webviewLoader);
                webviewDexElements = (Object[]) dexElementsField.get(webviewPathList);
                if (nativeLibPathElementsField != null) {
                    Object[] elements = (Object[]) nativeLibPathElementsField.get(webviewPathList);
                    if (elements != null) webviewNativeElements = elements;
                }
                Log.i(TAG, "主 WebView APK dex 元素数: " + webviewDexElements.length
                        + ", native 元素数: " + webviewNativeElements.length);
            }

            Object[] sharedDexElements = new Object[0];
            Object[] sharedNativeElements = new Object[0];
            if (sharedLibs != null && sharedLibs.length > 0) {
                String sharedPath = TextUtils.join(File.pathSeparator, sharedLibs);
                dalvik.system.PathClassLoader sharedLoader = new dalvik.system.PathClassLoader(
                        sharedPath, null, currentCl.getParent());
                Object sharedPathList = pathListField.get(sharedLoader);
                Object[] se = (Object[]) dexElementsField.get(sharedPathList);
                if (se != null) sharedDexElements = se;
                if (nativeLibPathElementsField != null) {
                    Object[] sne = (Object[]) nativeLibPathElementsField.get(sharedPathList);
                    if (sne != null) sharedNativeElements = sne;
                }
                Log.i(TAG, "SharedLibs dex 元素数: " + sharedDexElements.length);
            }

            Object currentPathList = pathListField.get(currentCl);
            Object[] oldDexElements = (Object[]) dexElementsField.get(currentPathList);
            int oldLen = (oldDexElements != null) ? oldDexElements.length : 0;
            int webLen = webviewDexElements.length;
            int sharedLen = sharedDexElements.length;

            if (webLen == 0 && sharedLen == 0) {
                Log.e(TAG, "没有提取到任何 dexElement");
                return false;
            }

            Object[] combined = (Object[]) java.lang.reflect.Array.newInstance(
                    oldDexElements != null
                            ? oldDexElements.getClass().getComponentType()
                            : webviewDexElements.getClass().getComponentType(),
                    webLen + sharedLen + oldLen);
            if (webLen > 0) System.arraycopy(webviewDexElements, 0, combined, 0, webLen);
            if (sharedLen > 0) System.arraycopy(sharedDexElements, 0, combined, webLen, sharedLen);
            if (oldLen > 0) System.arraycopy(oldDexElements, 0, combined, webLen + sharedLen, oldLen);
            dexElementsField.set(currentPathList, combined);
            Log.i(TAG, "dex 注入完成，总元素数: " + combined.length);

            if (nativeLibPathElementsField != null) {
                Object[] oldNativeElements = (Object[]) nativeLibPathElementsField.get(currentPathList);
                int oldNativeLen = (oldNativeElements != null) ? oldNativeElements.length : 0;
                int webNativeLen = webviewNativeElements.length;
                int sharedNativeLen = sharedNativeElements.length;
                if (webNativeLen > 0 || sharedNativeLen > 0) {
                    Class<?> componentType = webNativeLen > 0
                            ? webviewNativeElements.getClass().getComponentType()
                            : (oldNativeLen > 0 ? oldNativeElements.getClass().getComponentType()
                            : sharedNativeElements.getClass().getComponentType());
                    Object[] combinedNative = (Object[]) java.lang.reflect.Array.newInstance(
                            componentType, webNativeLen + sharedNativeLen + oldNativeLen);
                    if (webNativeLen > 0)
                        System.arraycopy(webviewNativeElements, 0, combinedNative, 0, webNativeLen);
                    if (sharedNativeLen > 0)
                        System.arraycopy(sharedNativeElements, 0, combinedNative, webNativeLen, sharedNativeLen);
                    if (oldNativeLen > 0)
                        System.arraycopy(oldNativeElements, 0, combinedNative, webNativeLen + sharedNativeLen, oldNativeLen);
                    nativeLibPathElementsField.set(currentPathList, combinedNative);
                    Log.i(TAG, "native 路径注入完成，总元素数: " + combinedNative.length);
                }
            }

            return true;
        } catch (Throwable t) {
            Log.e(TAG, "dex 注入失败", t);
            return false;
        }
    }

    /**
     * 加载真实 Service 并 attach {@code contextForAttach}。
     * <p>
     * {@code useAppClassLoaderForRealClass} 为 true 时（WebView 包 Context + 已 injectDex）：
     * 用宿主 {@link #mAppContext} 的 ClassLoader 加载类，与 Binder 解 Bundle 使用的 LoadedApk CL 一致，
     * 避免 {@code FileDescriptorInfo} 双加载导致 {@code ArrayStoreException}；attach 仍用 WebView 包装
     * Context，以保留资源与 native namespace。
     */
    private boolean initRealService(String realClassName, Context contextForAttach,
                                    boolean useAppClassLoaderForRealClass) {
        try {
            ClassLoader cl = (useAppClassLoaderForRealClass && mAppContext != null)
                    ? mAppContext.getClassLoader()
                    : contextForAttach.getClassLoader();
            Class<?> realClass = cl.loadClass(realClassName);
            mRealService = (Service) realClass.newInstance();

            Method attachBaseContext = ContextWrapper.class
                    .getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);
            attachBaseContext.invoke(mRealService, contextForAttach);

            mOnBindMethod = realClass.getMethod("onBind", Intent.class);
            mOnBindMethod.setAccessible(true);
            try {
                mOnDestroyMethod = realClass.getMethod("onDestroy");
                mOnDestroyMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                mOnRebindMethod = realClass.getMethod("onRebind", Intent.class);
                mOnRebindMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
            }

            Log.i(TAG, "驱动真实沙盒服务 onCreate: " + realClassName);
            Method onCreateMethod = realClass.getMethod("onCreate");
            onCreateMethod.setAccessible(true);
            onCreateMethod.invoke(mRealService);
            Log.i(TAG, "真实沙盒服务 onCreate 成功");

            return true;
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "找不到沙盒入口类: " + e.getMessage());
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "真实沙盒服务初始化失败", t);
            return false;
        }
    }

    /**
     * 包装 {@link Context#createPackageContext} 结果：保留 WebView 包的 ClassLoader / 资源 / linker，
     * 但 {@link #getApplicationContext()} 返回 {@link SandboxedApplicationContext}，
     * 保证 Chromium 通过 {@code ContextUtils.getApplicationContext().getAssets()} 能访问
     * WebView APK 内的 assets（icudtl.dat / v8 snapshot 等）。
     */
    private static final class SandboxedWebViewServiceContext extends ContextWrapper {

        private final Context mAppContextProxy;

        SandboxedWebViewServiceContext(Context webViewPackageContext, Context hostAppContext) {
            super(webViewPackageContext);
            mAppContextProxy = new SandboxedApplicationContext(
                    hostAppContext != null ? hostAppContext : webViewPackageContext,
                    webViewPackageContext);
        }

        @Override
        public Context getApplicationContext() {
            return mAppContextProxy;
        }
    }

    /**
     * 包装宿主 Application Context：
     * <ul>
     *   <li>{@link #getAssets()} / {@link #getResources()} 代理到 WebView 包 Context，
     *       使 Chromium native 层 {@code base::android::OpenApkAsset("assets/icudtl.dat")}
     *       通过 {@code ContextUtils.getApplicationContext().getAssets().openNonAssetFd()}
     *       能找到 WebView APK 中的资源文件。</li>
     *   <li>其余方法（getPackageName / getApplicationInfo / getSystemService 等）仍由
     *       宿主 Application 处理，保持 Chromium Binder IPC 的正确语义。</li>
     * </ul>
     */
    private static final class SandboxedApplicationContext extends ContextWrapper {

        private final Context mWebViewContext;

        SandboxedApplicationContext(Context hostAppContext, Context webViewContext) {
            super(hostAppContext);
            mWebViewContext = webViewContext;
        }

        @Override
        public AssetManager getAssets() {
            return mWebViewContext.getAssets();
        }

        @Override
        public Resources getResources() {
            return mWebViewContext.getResources();
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }
    }
}
