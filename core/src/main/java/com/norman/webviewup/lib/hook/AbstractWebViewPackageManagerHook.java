package com.norman.webviewup.lib.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.binder.BinderHook;
import com.norman.webviewup.lib.service.binder.ProxyBinder;
import com.norman.webviewup.lib.service.interfaces.IActivityThread;
import com.norman.webviewup.lib.service.interfaces.IApplicationInfo;
import com.norman.webviewup.lib.service.interfaces.IContextImpl;
import com.norman.webviewup.lib.service.interfaces.IPackageManager;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.proxy.PackageManagerProxy;
import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.ProcessUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

/**
 * 主进程 {@link PackageManagerServiceHook} 与沙盒子进程 {@link SandboxedProcessPackageManagerHook}
 * 共用的 PMS 代理逻辑：把目标 WebView 包名解析到本地 APK / native lib 路径。
 */
abstract class AbstractWebViewPackageManagerHook extends BinderHook {

    private static final String TAG = "WebViewPMHook";

    protected final Context context;
    protected final String webViewPackageName;
    protected final String apkPath;
    protected final String libsPath;

    private Map<String, IBinder> binderCacheMap;

    protected ApplicationInfo cachedWebViewAppInfo;

    protected AbstractWebViewPackageManagerHook(@NonNull Context context,
                                                @NonNull String packageName,
                                                @NonNull String apkPath,
                                                @NonNull String libsPath) {
        this.context = context;
        this.webViewPackageName = packageName;
        this.apkPath = apkPath;
        this.libsPath = libsPath;
    }

    protected ApplicationInfo getWebViewApplicationInfo(int flags) {
        PackageInfo pi = getWebViewPackageInfo(flags);
        return pi != null ? pi.applicationInfo : null;
    }

    protected PackageInfo getWebViewPackageInfo(int flags) {
        PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, flags);
        if (packageInfo == null) {
            flags &= ~PackageManager.GET_SIGNATURES;
            packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, flags);
        }
        if (packageInfo == null) {
            return null;
        }
        fillPackageInfo(packageInfo);
        return packageInfo;
    }

    /**
     * 已安装包常在 {@code lib/arm}、{@code lib/arm64} 下放置 so，与规范名 {@code armeabi-v7a}、{@code arm64-v8a} 不一致。
     * 与 {@link com.norman.webviewup.lib.util.ProcessUtils#getCurrentInstruction} 中 arm/arm64 语义对齐。
     */
    @Nullable
    private static String shortDirNameForCanonicalAbi(String canonical) {
        if ("arm64-v8a".equals(canonical)) {
            return "arm64";
        }
        if ("armeabi-v7a".equals(canonical) || "armeabi".equals(canonical)) {
            return "arm";
        }
        return null;
    }

    @Nullable
    private static String canonicalAbiFromDiskDirName(String diskName, String[] preferredAbis) {
        if (TextUtils.isEmpty(diskName)) {
            return null;
        }
        if ("arm64".equals(diskName)) {
            for (String a : preferredAbis) {
                if ("arm64-v8a".equals(a)) {
                    return a;
                }
            }
            for (String a : preferredAbis) {
                if (a != null && a.startsWith("arm64")) {
                    return a;
                }
            }
        }
        if ("arm".equals(diskName)) {
            for (String a : preferredAbis) {
                if ("armeabi-v7a".equals(a) || "armeabi".equals(a)) {
                    return a;
                }
            }
        }
        return null;
    }

    /**
     * 解析 {@code libsPath} 根目录下的 ABI 子目录：规范名匹配 → 短目录名（arm/arm64）→ 子目录内含 .so。
     */
    @Nullable
    private static Pair<String, String> resolveNativeLibraryDirFromLibsRoot(File libsDir, boolean is64Bit) {
        String[] preferredAbis =
                (is64Bit ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS).clone();
        String[] sortedAbis = preferredAbis.clone();
        Arrays.sort(sortedAbis);

        String[] list = libsDir.list();
        if (list == null) {
            return null;
        }

        for (String n : list) {
            if (n != null && n.endsWith(".so")) {
                String cpuAbi = preferredAbis.length > 0 ? preferredAbis[0] : null;
                if (cpuAbi == null) {
                    return null;
                }
                Log.w(TAG, "resolveNativeLibraryDirFromLibsRoot: so 在 lib 根目录，cpuAbi=" + cpuAbi);
                return Pair.create(cpuAbi, libsDir.getAbsolutePath());
            }
        }

        for (String name : list) {
            if (name != null && Arrays.binarySearch(sortedAbis, name) >= 0) {
                File d = new File(libsDir, name);
                if (d.isDirectory()) {
                    return Pair.create(name, d.getAbsolutePath());
                }
            }
        }

        for (String canonical : preferredAbis) {
            if (canonical == null) {
                continue;
            }
            File d = new File(libsDir, canonical);
            if (d.isDirectory()) {
                return Pair.create(canonical, d.getAbsolutePath());
            }
            String shortName = shortDirNameForCanonicalAbi(canonical);
            if (shortName != null) {
                File d2 = new File(libsDir, shortName);
                if (d2.isDirectory()) {
                    return Pair.create(canonical, d2.getAbsolutePath());
                }
            }
        }

        for (String name : list) {
            if (name == null) {
                continue;
            }
            File sub = new File(libsDir, name);
            if (!sub.isDirectory()) {
                continue;
            }
            String[] subList = sub.list();
            if (subList == null) {
                continue;
            }
            for (String f : subList) {
                if (f != null && f.endsWith(".so")) {
                    String cpuAbi = canonicalAbiFromDiskDirName(name, preferredAbis);
                    if (cpuAbi == null && Arrays.binarySearch(sortedAbis, name) >= 0) {
                        cpuAbi = name;
                    }
                    if (cpuAbi == null) {
                        cpuAbi = preferredAbis.length > 0 ? preferredAbis[0] : name;
                        Log.w(TAG, "resolveNativeLibraryDirFromLibsRoot: .so 兜底推断 cpuAbi=" + cpuAbi
                                + " dir=" + name);
                    }
                    return Pair.create(cpuAbi, sub.getAbsolutePath());
                }
            }
        }

        return null;
    }

    protected void fillPackageInfo(PackageInfo packageInfo) {
        boolean is64Bit = ProcessUtils.is64Bit();
        String[] preferredAbis =
                (is64Bit ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS).clone();

        File libsDir = new File(libsPath);
        if (!FileUtils.exist(libsDir)) {
            throw new RuntimeException("libsDir not exist  " + libsPath);
        }
        if (libsDir.list() == null) {
            throw new RuntimeException("abi dir  not exist in " + libsPath);
        }

        Pair<String, String> resolved = resolveNativeLibraryDirFromLibsRoot(libsDir, is64Bit);
        if (resolved == null) {
            throw new NullPointerException("unable to find supported abis "
                    + Arrays.toString(preferredAbis)
                    + " in dir " + libsPath);
        }
        String cpuAbi = resolved.first;
        String nativeLibraryDir = resolved.second;
        try {
            IApplicationInfo iApplicationInfo = RuntimeAccess.objectAccess(IApplicationInfo.class, packageInfo.applicationInfo);
            iApplicationInfo.setPrimaryCpuAbi(cpuAbi);
        } catch (Throwable ignore) {
        }

        try {
            IApplicationInfo iApplicationInfo = RuntimeAccess.objectAccess(IApplicationInfo.class, packageInfo.applicationInfo);
            iApplicationInfo.setNativeLibraryRootDir(libsPath);
        } catch (Throwable ignore) {
        }
        packageInfo.applicationInfo.nativeLibraryDir = nativeLibraryDir;
        // 无条件覆写 sourceDir / publicSourceDir：缓存的 LoadedApk 可能指向系统真实 WebView 路径
        // （不存在或版本不符），导致 ResourcesKey.mResDir=null，AssetManager 里找不到
        // assets/icudtl.dat，进而 Chromium 无法向渲染进程发送 ICU fd。
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_INSTALLED
                | ApplicationInfo.FLAG_HAS_CODE;

        cachedWebViewAppInfo = packageInfo.applicationInfo;
    }

    private final PackageManagerProxy proxy = new PackageManagerProxy() {

        @Override
        protected PackageInfo getPackageInfo(Object... args) {
            if (args == null || args.length < 1 || !(args[0] instanceof String)) {
                return (PackageInfo) invoke();
            }
            String packageName = (String) args[0];
            int flags = 0;
            if (args.length >= 2 && args[1] instanceof Number) {
                flags = ((Number) args[1]).intValue();
            }
            if (packageName.equals(webViewPackageName)) {
                PackageInfo packageInfo = getWebViewPackageInfo(flags);
                if (packageInfo == null) {
                    throw new RuntimeException("apkPath is not valid  " + apkPath);
                }
                return packageInfo;
            }
            return (PackageInfo) invoke();
        }

        @Override
        protected ApplicationInfo getApplicationInfo(Object... args) {
            if (args == null || args.length < 1 || !(args[0] instanceof String)) {
                return (ApplicationInfo) invoke();
            }
            String packageName = (String) args[0];
            int flags = 0;
            if (args.length >= 2 && args[1] instanceof Number) {
                flags = ((Number) args[1]).intValue();
            }
            if (packageName.equals(webViewPackageName)) {
                // 清除 LoadedApk 缓存，保证下次 createPackageContext 用我们的重新建
                // LoadedApk，从而 ResourcesKey.mResDir = apkPath 而非 null。
                invalidateLoadedApkCache(webViewPackageName);
                ApplicationInfo ai = getWebViewApplicationInfo(flags);
                if (ai == null) {
                    throw new RuntimeException("apkPath is not valid  " + apkPath);
                }
                return ai;
            }
            return (ApplicationInfo) invoke();
        }

        @Override
        protected int getComponentEnabledSetting(Object... args) {
            if (args == null || args.length < 1 || !(args[0] instanceof ComponentName)) {
                return (int) invoke();
            }
            ComponentName componentName = (ComponentName) args[0];
            if (componentName.getPackageName().equals(webViewPackageName)) {
                return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            } else {
                return (int) invoke();
            }
        }

        @Override
        protected ServiceInfo getServiceInfo(Object... args) {
            if (args == null || args.length < 1 || !(args[0] instanceof ComponentName)) {
                return (ServiceInfo) invoke();
            }
            ComponentName component = (ComponentName) args[0];
            return handleGetServiceInfo(component);
        }

        private ServiceInfo handleGetServiceInfo(ComponentName component) {
            if (component != null
                    && webViewPackageName.equals(component.getPackageName())
                    && component.getClassName() != null
                    && component.getClassName().contains("SandboxedProcessService")) {
                ServiceInfo info = new ServiceInfo();
                info.name = component.getClassName();
                info.packageName = component.getPackageName();
                info.exported = true;
                info.enabled = true;
                if (cachedWebViewAppInfo != null) {
                    info.applicationInfo = cachedWebViewAppInfo;
                } else {
                    info.applicationInfo = getWebViewApplicationInfo(0);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.flags |= ServiceInfo.FLAG_ISOLATED_PROCESS
                            | ServiceInfo.FLAG_EXTERNAL_SERVICE;
                }
                return info;
            }
            return (ServiceInfo) invoke();
        }



        @Override
        protected String getInstallerPackageName(String packageName) {
            if (packageName.equals(webViewPackageName)) {
                return "com.android.vending";
            } else {
                return (String) invoke();
            }
        }

        @Override
        protected IBinder asBinder() {
            IBinder proxyBinder = getProxyBinder();
            return proxyBinder != null ? proxyBinder : (IBinder) invoke();
        }
    };

    @Override
    protected IBinder onTargetBinderObtain() {
        IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);
        return serviceManager.getService(IPackageManager.SERVICE);
    }

    @Override
    protected ProxyBinder onProxyBinderCreate(IBinder binder) {
        IPackageManager service = RuntimeAccess.staticAccess(IPackageManager.class);
        IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);

        IInterface targetInterface = service.asInterface(binder);
        proxy.setTarget(targetInterface);
        IInterface proxyInterface = (IInterface) proxy.get();
        ProxyBinder proxyBinder = new ProxyBinder(targetInterface, proxyInterface);

        binderCacheMap = serviceManager.getServiceCache();
        return proxyBinder;
    }

    @Override
    protected void onTargetBinderRestore(IBinder binder) {
        IInterface targetInterface;
        try {
            targetInterface = binder.queryLocalInterface(binder.getInterfaceDescriptor());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        binderCacheMap.put(IPackageManager.SERVICE, binder);
        updateActivityThreadPackageManager(targetInterface);
        flushContextImplPackageManager();
    }

    @Override
    protected void onProxyBinderReplace(ProxyBinder binder) {
        binderCacheMap.put(IPackageManager.SERVICE, binder);
        updateActivityThreadPackageManager(binder.getProxyIInterface());
        flushContextImplPackageManager();
    }

    private static void updateActivityThreadPackageManager(IInterface iInterface) {
        IActivityThread activityThread = RuntimeAccess.staticAccess(IActivityThread.class);
        activityThread.setPackageManager(iInterface);
    }

    /**
     * 沿 ContextWrapper 链找到 ContextImpl，清空其缓存的 PackageManager，使后续查询走新代理。
     */
    /**
     * 清除 ActivityThread 中对指定包名 LoadedApk 的缓存（mPackages / mResourcePackages）。
     * <p>
     * 背景：系统可能缓存了指向真实已安装 WebView 路径的 LoadedApk；该路径对我们的私有 APK 无效，
     * 导致 ResourcesKey.mResDir=null（APK 被归入 mLibDirs），AssetManager 无法搜到
     * assets/icudtl.dat，最终 Chromium 向渲染进程发送 fd=-1 而崩溃。
     * 清除缓存后，下一次 createPackageContext 会用我们 hook 返回的 ApplicationInfo
     * （publicSourceDir=apkPath）重建 LoadedApk，确保 mResDir = apkPath。
     */
    static void invalidateLoadedApkCache(String packageName) {
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

    private void flushContextImplPackageManager() {
        Context baseContext = context.getApplicationContext();
        if (baseContext == null) {
            return;
        }
        while (baseContext instanceof ContextWrapper) {
            baseContext = ((ContextWrapper) baseContext).getBaseContext();
        }
        try {
            IContextImpl contextImpl = RuntimeAccess.objectAccess(IContextImpl.class, baseContext);
            contextImpl.setPackageManager(null);
        } catch (Throwable ignored) {
        }
    }
}
