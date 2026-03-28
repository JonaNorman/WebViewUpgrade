package com.norman.webviewup.lib.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
 * Stub 服务运行在宿主 App 的普通子进程中（非 isolated），因此可以直接读取宿主私有目录下的 APK，
 * 无需 ashmem / FD 传递等复杂机制。
 */
public class ActivityManagerHook extends BinderHook {

    private static final String TAG = "ActivityManagerHook";

    private static final String SANDBOX_SERVICE_NAME = "SandboxedProcessService";

    private static final Pattern SANDBOX_SERVICE_INDEX_PATTERN =
            Pattern.compile(SANDBOX_SERVICE_NAME + "(\\d+)$");

    private static final String STUB_SERVICE_CLASS_PREFIX =
            "com.norman.webviewup.lib.service.stub.Stub" + SANDBOX_SERVICE_NAME;

    private static final int MAX_STUB_SERVICES = 5;

    private final Context context;
    private final String hostPackageName;
    private String targetWebViewPackage;
    private String webViewApkPath;
    private String webViewLibsPath;

    private Object mOriginalAm;
    private ISingleton mSingleton;

    /**
     * @param context              宿主 App Context
     * @param targetWebViewPackage 目标热替换 WebView 包名
     * @param webViewApkPath       热替换后的 WebView APK 文件路径
     * @param webViewLibsPath      WebView 原生库目录路径
     */
    public ActivityManagerHook(Context context, String targetWebViewPackage,
                               String webViewApkPath, String webViewLibsPath) {
        this.context = context;
        this.targetWebViewPackage = targetWebViewPackage;
        this.webViewApkPath = webViewApkPath;
        this.webViewLibsPath = webViewLibsPath;
        this.hostPackageName = context.getPackageName();
    }

    public void update(String targetWebViewPackage, String webViewApkPath, String webViewLibsPath) {
        this.targetWebViewPackage = targetWebViewPackage;
        this.webViewApkPath = webViewApkPath;
        this.webViewLibsPath = webViewLibsPath;
    }

    @Override
    protected IBinder onTargetBinderObtain() {
        return null;
    }

    @Override
    protected ProxyBinder onProxyBinderCreate(IBinder ignored) {
        Object gDefault;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        ActivityManagerProxy proxy = new ActivityManagerProxy() {
            @Override
            protected Object bindIsolatedService(Object... args) {
                debugTrace("H3", "bindIsolatedService", "intercept", summarizeArgs(args));
                Intent redirectedIntent = findAndRedirectIntent(args);
                if (redirectedIntent != null) {
                    return callBindServiceOnRealAM(args);
                }
                return invoke();
            }

            @Override
            protected Object bindServiceInstance(Object... args) {
                debugTrace("H1", "bindServiceInstance", "intercept", summarizeArgs(args));
                Intent redirectedIntent = findAndRedirectIntent(args);
                if (redirectedIntent != null) {
                    return callBindServiceOnRealAM(args);
                }
                return invoke();
            }
        };

        proxy.setTarget(mOriginalAm);
        Object proxyAm = proxy.get();

        mSingleton.setInstance(proxyAm);
        return null;
    }

    @Override
    protected void onTargetBinderRestore(IBinder binder) {
        if (mSingleton != null && mOriginalAm != null) {
            mSingleton.setInstance(mOriginalAm);
            Log.i(TAG, "ActivityManagerHook 已恢复");
        }
    }

    @Override
    protected void onProxyBinderReplace(ProxyBinder proxyBinder) {
        Log.i(TAG, "ActivityManagerHook 激活成功，宿主包名=" + hostPackageName
                + "，监控目标=" + targetWebViewPackage);
    }

    // -----------------------------------------------------------------------
    //  重定向核心逻辑
    // -----------------------------------------------------------------------

    /**
     * 在参数数组中找到 Intent，执行重定向逻辑。
     * 返回被重定向的 Intent（表示需要转调 bindService），或 null（表示不拦截）。
     */
    private Intent findAndRedirectIntent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof Intent) {
                Intent intent = (Intent) arg;
                if (redirectSandboxServiceIfNeeded(intent)) {
                    return intent;
                }
            }
        }
        return null;
    }

    /**
     * 检查并重定向 Intent 到 StubService。
     * 返回 true 表示已重定向（需要转调 bindService），false 表示不拦截。
     */
    private boolean redirectSandboxServiceIfNeeded(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) return false;

        // Chromium 可能先走 bindIsolatedService（已把 Intent 改成 Stub），再走 bindServiceInstance。
        // 此时 Component 已是宿主包名，若这里返回 false 会走 invoke() → 真实 bindServiceInstance，
        // 系统不允许对非 isolated 的 Stub 使用 instance name，直接抛 IllegalArgumentException。
        if (hostPackageName.equals(component.getPackageName())
                && component.getClassName() != null
                && component.getClassName().startsWith(STUB_SERVICE_CLASS_PREFIX)) {
            debugTrace("H6", "redirectSandboxServiceIfNeeded", "intent already stub",
                    component.flattenToShortString());
            ensureSandboxIntentExtras(intent);
            return true;
        }

        if (!targetWebViewPackage.equals(component.getPackageName())) {
            return false;
        }

        String className = component.getClassName();
        if (className == null || !className.contains(SANDBOX_SERVICE_NAME)) {
            return false;
        }

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

        Bundle extras = intent.getExtras();
        if (extras == null) {
            extras = new Bundle();
        }

        extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_APK_PATH, webViewApkPath);
        extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_LIBS_PATH, webViewLibsPath);
        extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_PACKAGE_NAME, targetWebViewPackage);
        extras.putString(SandboxedProcessServiceDelegate.EXTRA_ORIGINAL_SERVICE_CLASS, className);

        try {
            PackageInfo packageInfo = WebViewReplace.REPLACE_WEB_VIEW_PACKAGE_INFO;
            if (packageInfo != null && packageInfo.applicationInfo != null
                    && packageInfo.applicationInfo.sharedLibraryFiles != null) {
                extras.putStringArray(SandboxedProcessServiceDelegate.EXTRA_SHARED_LIBS,
                        packageInfo.applicationInfo.sharedLibraryFiles);
            }
        } catch (Throwable t) {
            Log.e(TAG, "获取 WebView SharedLibs 失败", t);
        }

        intent.putExtras(extras);

        Log.i(TAG,
                "沙盒进程重定向: " + component.flattenToShortString()
                        + " → " + stubComponent.flattenToShortString()
                        + " (原编号=" + sandboxIndex + ", Stub编号=" + stubIndex + ")"
                        + " apkPath=" + webViewApkPath);

        intent.setComponent(stubComponent);
        return true;
    }

    /** 第二次及以后的 AMS 调用可能仍携带同一 Intent，补全 extras 避免子进程缺路径。 */
    private void ensureSandboxIntentExtras(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            extras = new Bundle();
            intent.putExtras(extras);
        }
        if (webViewApkPath != null) {
            extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_APK_PATH, webViewApkPath);
        }
        if (webViewLibsPath != null) {
            extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_LIBS_PATH, webViewLibsPath);
        }
        if (targetWebViewPackage != null) {
            extras.putString(SandboxedProcessServiceDelegate.EXTRA_WEBVIEW_PACKAGE_NAME, targetWebViewPackage);
        }
        try {
            PackageInfo packageInfo = WebViewReplace.REPLACE_WEB_VIEW_PACKAGE_INFO;
            if (packageInfo != null && packageInfo.applicationInfo != null
                    && packageInfo.applicationInfo.sharedLibraryFiles != null) {
                extras.putStringArray(SandboxedProcessServiceDelegate.EXTRA_SHARED_LIBS,
                        packageInfo.applicationInfo.sharedLibraryFiles);
            }
        } catch (Throwable t) {
            Log.e(TAG, "ensureSandboxIntentExtras: SharedLibs 失败", t);
        }
    }

    /**
     * 将 bindIsolatedService / bindServiceInstance 的参数适配后，转调 AMS 的 bindService。
     * <p>
     * bindIsolatedService 参数列表（API 29+）：
     *   (IApplicationThread caller, IBinder token, Intent service, String resolvedType,
     *    IServiceConnection connection, int/long flags, String instanceName,
     *    String callingPackage, int userId)
     * <p>
     * bindService 参数列表：
     *   (IApplicationThread caller, IBinder token, Intent service, String resolvedType,
     *    IServiceConnection connection, int/long flags, String callingPackage, int userId)
     * <p>
     * 差异：去掉 instanceName，flags 去掉 BIND_EXTERNAL_SERVICE。
     */
    private Object callBindServiceOnRealAM(Object[] originalArgs) {
        try {
            // 遍历 IActivityManager 的方法，找到 bindService
            Method bindServiceMethod = findBindServiceMethod();
            if (bindServiceMethod == null) {
                debugTrace("H2", "callBindServiceOnRealAM", "bindService method not found",
                        summarizeArgs(originalArgs));
                Log.e(TAG, "无法找到 AMS 的 bindService 方法，降级调用原方法");
                return null;
            }

            debugTrace("H2", "callBindServiceOnRealAM", "selected bindService method",
                    summarizeMethod(bindServiceMethod));
            Object[] adaptedArgs = adaptArgsForBindService(originalArgs, bindServiceMethod);
            debugTrace("H1", "callBindServiceOnRealAM", "adapted args",
                    summarizeArgs(adaptedArgs));
            Log.i(TAG, "bindService method=" + summarizeMethod(bindServiceMethod)
                    + " src=" + summarizeArgs(originalArgs)
                    + " adapted=" + summarizeArgs(adaptedArgs));
            return bindServiceMethod.invoke(mOriginalAm, adaptedArgs);
        } catch (Throwable t) {
            debugTrace("H1", "callBindServiceOnRealAM", "invoke bindService failed",
                    String.valueOf(t));
            Log.e(TAG, "转调 bindService 失败", t);
            return null;
        }
    }

    private Method findBindServiceMethod() {
        try {
            Class<?> amClass = mOriginalAm.getClass();
            // 查找所有接口中的 bindService 方法
            for (Class<?> iface : amClass.getInterfaces()) {
                for (Method m : iface.getDeclaredMethods()) {
                    if ("bindService".equals(m.getName())) {
                        return m;
                    }
                }
            }
            // 没有在接口上找到，尝试直接从类查找
            for (Method m : amClass.getMethods()) {
                if ("bindService".equals(m.getName())) {
                    return m;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "查找 bindService 方法失败", t);
        }
        return null;
    }

    /**
     * 将 bindIsolatedService 的参数数组适配成 bindService 的参数。
     * 核心区别：去掉 instanceName（String 类型）参数，清除 BIND_EXTERNAL_SERVICE flag。
     * <p>
     * bindIsolatedService: caller, token, intent, resolvedType, conn, flags, instanceName, pkg, userId
     * bindService:         caller, token, intent, resolvedType, conn, flags, pkg, userId
     */
    private Object[] adaptArgsForBindService(Object[] srcArgs, Method bindServiceMethod) {
        int targetParamCount = bindServiceMethod.getParameterTypes().length;
        Class<?>[] targetTypes = bindServiceMethod.getParameterTypes();
        List<Object> mutable = new ArrayList<>();
        if (srcArgs != null) {
            for (Object arg : srcArgs) {
                mutable.add(arg);
            }
        }

        // bindServiceInstance 相比 bindService 额外携带 instanceName，优先删除该参数。
        while (mutable.size() > targetParamCount) {
            int removeIndex = findInstanceNameIndex(mutable);
            if (removeIndex < 0) {
                removeIndex = findNullableGapIndex(mutable);
            }
            if (removeIndex < 0) {
                // 兜底：删除 flags 后第一个 String（最接近 instanceName 的位置）
                int flagsIndex = findFlagsIndex(mutable);
                removeIndex = (flagsIndex >= 0 && flagsIndex + 1 < mutable.size()) ? flagsIndex + 1 : mutable.size() - 1;
            }
            mutable.remove(removeIndex);
        }

        Object[] result = new Object[targetParamCount];
        for (int i = 0; i < targetParamCount; i++) {
            Object value = i < mutable.size() ? mutable.get(i) : null;
            result[i] = coerceValueForType(value, targetTypes[i]);
        }

        clearExternalServiceFlag(result, targetTypes);
        return result;
    }

    private void clearExternalServiceFlag(Object[] args, Class<?>[] types) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) {
                int flags = (int) args[i];
                // BIND_EXTERNAL_SERVICE: old value 0x00800000, new value on Android 14+ = 0x80000000
                args[i] = flags & ~0x00800000 & 0x7FFFFFFF;
                return;
            } else if (args[i] instanceof Long) {
                long flags = (long) args[i];
                // Clear both old (0x00800000) and new (0x80000000) BIND_EXTERNAL_SERVICE values
                args[i] = flags & ~0x00800000L & ~0x80000000L;
                return;
            }
        }
    }

    private int findFlagsIndex(List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (value instanceof Long || value instanceof Integer) {
                return i;
            }
        }
        return -1;
    }

    private int findUserIdIndex(List<Object> args) {
        for (int i = args.size() - 1; i >= 0; i--) {
            Object value = args.get(i);
            if (value instanceof Integer) {
                return i;
            }
        }
        return -1;
    }

    private int findInstanceNameIndex(List<Object> args) {
        int flagsIndex = findFlagsIndex(args);
        int userIdIndex = findUserIdIndex(args);
        if (flagsIndex < 0 || userIdIndex <= flagsIndex) {
            return -1;
        }
        for (int i = flagsIndex + 1; i < userIdIndex; i++) {
            Object value = args.get(i);
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String) value;
            // callingPackage 通常等于宿主包名，instanceName/featureId 一般不同。
            if (text != null && text.equals(hostPackageName)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private int findNullableGapIndex(List<Object> args) {
        int flagsIndex = findFlagsIndex(args);
        int userIdIndex = findUserIdIndex(args);
        if (flagsIndex < 0 || userIdIndex <= flagsIndex) {
            return -1;
        }
        for (int i = flagsIndex + 1; i < userIdIndex; i++) {
            if (args.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private Object coerceValueForType(Object value, Class<?> targetType) {
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0L;
        }
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return false;
        }
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        return value;
    }

    private boolean isBoxedMatch(Class<?> expected, Object value) {
        if (expected == int.class) return value instanceof Integer;
        if (expected == long.class) return value instanceof Long;
        if (expected == boolean.class) return value instanceof Boolean;
        return false;
    }

    private String summarizeMethod(Method m) {
        if (m == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append(m.getDeclaringClass().getName()).append("#").append(m.getName()).append("(");
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(types[i].getSimpleName());
        }
        sb.append(")->").append(m.getReturnType().getSimpleName());
        return sb.toString();
    }

    private String summarizeArgs(Object[] args) {
        if (args == null) return "args=null";
        StringBuilder sb = new StringBuilder();
        sb.append("len=").append(args.length);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            sb.append(" | ").append(i).append(":");
            if (arg == null) {
                sb.append("null");
                continue;
            }
            sb.append(arg.getClass().getSimpleName()).append("=");
            if (arg instanceof Intent) {
                ComponentName c = ((Intent) arg).getComponent();
                sb.append(c != null ? c.flattenToShortString() : "intent_no_component");
            } else if (arg instanceof String || arg instanceof Integer || arg instanceof Long
                    || arg instanceof Boolean) {
                sb.append(arg);
            } else {
                sb.append(arg.getClass().getName());
            }
        }
        return sb.toString();
    }

    /** 调试用：adb logcat -s ActivityManagerHook:D */
    private static void debugTrace(String hypothesisId, String where, String message, String data) {
        Log.d(TAG, "[" + hypothesisId + "] " + where + " | " + message + " | " + data);
    }
}
