package com.norman.webviewup.lib.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import com.norman.webviewup.lib.WebViewReplace;
import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.SandboxedProcessServiceDelegate;
import com.norman.webviewup.lib.service.binder.BinderHook;
import com.norman.webviewup.lib.service.binder.ProxyBinder;
import com.norman.webviewup.lib.service.interfaces.IActivityManager;
import com.norman.webviewup.lib.service.interfaces.IActivityManagerNative;
import com.norman.webviewup.lib.service.interfaces.ISingleton;
import com.norman.webviewup.lib.service.proxy.ActivityManagerProxy;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hook ActivityManager 实现沙盒进程替身重定向。
 *
 * 当 Chromium 通过 bindIsolatedService / bindServiceInstance 尝试拉起 WebView 包中的
 * SandboxedProcessService{N} 时，本 Hook 将 Intent 的 ComponentName 替换为
 * core 库中预注册的 StubSandboxedProcessService{N}，并将热替换后的 WebView APK
 * 路径注入到 Intent extras，供沙盒进程内的 SandboxedProcessServiceDelegate 动态加载 dex。
 *
 * 重要：此 Hook 必须是永久性的，不能 restore。
 */
public class ActivityManagerHook extends BinderHook {

    private static final String TAG = "ActivityManagerHook";

    /**
     * Chromium 沙盒进程服务名称基础标识。
     */
    private static final String SANDBOX_SERVICE_NAME = "SandboxedProcessService";

    /**
     * 匹配 Chromium 沙盒进程服务类名中的数字编号。
     * 例如：org.chromium.content.app.SandboxedProcessService0 → 提取 "0"
     */
    private static final Pattern SANDBOX_SERVICE_INDEX_PATTERN =
            Pattern.compile(SANDBOX_SERVICE_NAME + "(\\d+)$");

    /**
     * core 模块中预置的 Stub 服务的全限定类名前缀。
     */
    private static final String STUB_SERVICE_CLASS_PREFIX =
            "com.norman.webviewup.lib.service.stub.Stub" + SANDBOX_SERVICE_NAME;

    /**
     * 最大支持的 Stub 服务数量，与 AndroidManifest.xml 中注册的数量一致。
     */
    private static final int MAX_STUB_SERVICES = 5;

    private final Context context;
    private final String hostPackageName;
    private String targetWebViewPackage;
    private String webViewApkPath;

    /** 保存原始 IActivityManager 实例，供 restore 使用 */
    private Object mOriginalAm;

    /** 保存 Singleton 容器引用，用于 hook/restore 时写入 mInstance 字段 */
    private ISingleton mSingleton;

    /**
     * @param context              宿主 App Context
     * @param targetWebViewPackage 目标热替换 WebView 包名（如 com.google.android.webview）
     * @param webViewApkPath       热替换后的 WebView APK 文件路径
     */
    public ActivityManagerHook(Context context, String targetWebViewPackage, String webViewApkPath) {
        this.context = context;
        this.targetWebViewPackage = targetWebViewPackage;
        this.webViewApkPath = webViewApkPath;
        this.hostPackageName = context.getPackageName();
    }

    /**
     * 更新 Hook 的目标参数（用于重复调用 replace 时的热更新）。
     */
    public void update(String targetWebViewPackage, String webViewApkPath) {
        this.targetWebViewPackage = targetWebViewPackage;
        this.webViewApkPath = webViewApkPath;
    }

    // -----------------------------------------------------------------------
    //  BinderHook 实现
    // -----------------------------------------------------------------------

    @Override
    protected IBinder onTargetBinderObtain() {
        // IActivityManager 通过 Singleton 持有，不走 ServiceManager，这里返回 null 占位
        return null;
    }


    @Override
    protected ProxyBinder onProxyBinderCreate(IBinder ignored) {
        // 获取持有 IActivityManager 的 Singleton 容器
        Object gDefault;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            IActivityManager am = RuntimeAccess.staticAccess(IActivityManager.class);
            gDefault = am.getIActivityManagerSingleton();
        } else {
            IActivityManagerNative amn = RuntimeAccess.staticAccess(IActivityManagerNative.class);
            gDefault = amn.getGDefault();
        }

        mSingleton = RuntimeAccess.objectAccess(ISingleton.class, gDefault);
        mOriginalAm = mSingleton.getInstance();

        if (mOriginalAm == null) {
            Log.e(TAG, "IActivityManager 实例为 null，无法 hook");
            return null;
        }

        // 用框架代理替换 IActivityManager 实例
        ActivityManagerProxy proxy = new ActivityManagerProxy() {
            @Override
            protected Object bindIsolatedService(Object... args) {
                redirectSandboxServiceInArgs(args);
                return invoke();
            }

            @Override
            protected Object bindServiceInstance(Object... args) {
                redirectSandboxServiceInArgs(args);
                return invoke();
            }
        };

        proxy.setTarget(mOriginalAm);
        Object proxyAm = proxy.get();

        // 直接把代理写入 Singleton.mInstance（IActivityManager 不走普通 Binder 体系）
        mSingleton.setInstance(proxyAm);
        // 返回 null，onProxyBinderReplace 不会被实际使用（避免 ProxyBinder 包装逻辑干扰）
        return null;
    }

    @Override
    protected void onTargetBinderRestore(IBinder binder) {
        // restore 时将原始实例写回 Singleton
        if (mSingleton != null && mOriginalAm != null) {
            mSingleton.setInstance(mOriginalAm);
            Log.i(TAG, "ActivityManagerHook 已恢复");
        }
    }

    @Override
    protected void onProxyBinderReplace(ProxyBinder proxyBinder) {
        // ProxyBinder 机制不适用于 IActivityManager（无 ServiceManager Binder 路径）
        // 实际的写入已经在 onProxyBinderCreate 中直接完成，这里不需要做任何事
        Log.i(TAG, "ActivityManagerHook 激活成功，宿主包名=" + hostPackageName
                + "，监控目标=" + targetWebViewPackage);
    }

    // -----------------------------------------------------------------------
    //  重定向核心逻辑
    // -----------------------------------------------------------------------

    /**
     * 在参数数组中找到 Intent，检查是否需要重定向到 StubSandboxedProcessService。
     * fuzzy 模式下 args 就是系统传来的完整原始参数数组，直接遍历取 Intent 即可。
     */
    private void redirectSandboxServiceInArgs(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                redirectSandboxServiceIfNeeded((Intent) arg);
            }
        }
    }

    private void redirectSandboxServiceIfNeeded(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) return;

        // 只处理来自目标 WebView 包的请求，避免对自身 Stub Service 的二次重定向
        if (!targetWebViewPackage.equals(component.getPackageName())) {
            return;
        }

        String className = component.getClassName();
        if (className == null || !className.contains(SANDBOX_SERVICE_NAME)) {
            return;
        }

        // 提取沙盒编号
        Matcher matcher = SANDBOX_SERVICE_INDEX_PATTERN.matcher(className);
        int sandboxIndex = 0;
        if (matcher.find()) {
            try {
                sandboxIndex = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                Log.w(TAG, "无法解析沙盒编号，使用 0: " + className);
            }
        }

        int stubIndex = sandboxIndex % MAX_STUB_SERVICES;
        String stubClassName = STUB_SERVICE_CLASS_PREFIX + stubIndex;
        ComponentName stubComponent = new ComponentName(hostPackageName, stubClassName);

        // ====================================================================================
        // 终极解法：使用 Binder Wrapper 绕过 AMS 对 FileDescriptors 的硬拦截
        // 问题：如果直接往 Intent 里面 putExtra(PFD)，ActivityManagerService 会在 IPC 服务端
        //       执行 hasFileDescriptors() 强硬检查，如果发现有文件描述符直接抛出异常，这是无解的。
        // 方案：我们把 PFD 的获取逻辑封装在一个匿名的 IBinder 里！IBinder 是普通对象，
        //       不会触发 hasFileDescriptors()，并且可以完美在 Intent 的 Bundle 之间跨越进程边界。
        // ====================================================================================
        if (webViewApkPath != null) {
            Binder fdProvider = new Binder() {
                @Override
                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code == IBinder.FIRST_CALL_TRANSACTION) {
                        try {
                            // 在主进程实时 open，拥有完全文件权限
                            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                                    new File(webViewApkPath),
                                    ParcelFileDescriptor.MODE_READ_ONLY);

                            reply.writeNoException();
                            reply.writeInt(1); // 标记有数据
                            pfd.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                            return true;
                        } catch (Exception e) {
                            reply.writeException(e);
                            return true;
                        }
                    }
                    return super.onTransact(code, data, reply, flags);
                }
            };

            Bundle extras = intent.getExtras();
            if (extras == null) {
                extras = new Bundle();
            }
            extras.putBinder(SandboxedProcessServiceDelegate.EXTRA_APK_FD_BINDER, fdProvider);

            // 【关键】很多最新版的 WebView (如 >API 29 的 Trichrome) 把核心逻辑(如 ChildProcessServiceDelegate)
            // 放在了额外的 TrichromeLibrary 中！必须连同 SharedLibraries 一并传给沙盒进程。
            try {
                PackageInfo packageInfo = WebViewReplace.REPLACE_WEB_VIEW_PACKAGE_INFO;
                if (packageInfo != null && packageInfo.applicationInfo != null && packageInfo.applicationInfo.sharedLibraryFiles != null) {
                    extras.putStringArray(SandboxedProcessServiceDelegate.EXTRA_SHARED_LIBS, packageInfo.applicationInfo.sharedLibraryFiles);
                }
            } catch (Throwable t) {
                Log.e(TAG, "获取 WebView SharedLibs 失败", t);
            }

            intent.putExtras(extras);
        }

        Log.i(TAG,
                "沙盒进程重定向: " + component.flattenToShortString()
                        + " → " + stubComponent.flattenToShortString()
                        + " (原编号=" + sandboxIndex + ", Stub编号=" + stubIndex + ")"
                        + " apkPath=" + webViewApkPath);

        intent.setComponent(stubComponent);
    }
}
