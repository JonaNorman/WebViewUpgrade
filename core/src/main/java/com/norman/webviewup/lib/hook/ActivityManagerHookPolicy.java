package com.norman.webviewup.lib.hook;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ActivityManagerHook} 的可选策略：沙盒 Service 类名匹配、Stub 槽数量上限。
 * <p>
 * 默认行为与未配置时一致：类名包含 {@code SandboxedProcessService}，Stub 个数从 Manifest 解析，失败则用 5。
 */
public final class ActivityManagerHookPolicy {

    private static final int FALLBACK_STUB_COUNT = 5;

    @Nullable
    private static volatile Pattern sandboxServiceClassPattern;

    /** &lt;=0 表示未覆盖，使用自动探测 */
    private static volatile int maxStubServicesOverride;

    private ActivityManagerHookPolicy() {
    }

    /**
     * 为 null 时使用内置规则：类名包含 {@code SandboxedProcessService}。
     * 非 null 时对完整类名执行 {@link Pattern#matcher(CharSequence)}.{@link Matcher#find()}。
     */
    public static void setSandboxServiceClassPattern(@Nullable Pattern pattern) {
        sandboxServiceClassPattern = pattern;
    }

    @Nullable
    public static Pattern getSandboxServiceClassPattern() {
        return sandboxServiceClassPattern;
    }

    /** 显式指定 Stub 数量上限（与 Manifest 中 StubSandboxedProcessService0..N-1 一致）。&lt;=0 取消覆盖。 */
    public static void setMaxStubServicesOverride(int count) {
        maxStubServicesOverride = count;
    }

    public static int getMaxStubServicesOverride() {
        return maxStubServicesOverride;
    }

    public static int resolveMaxStubServices(Context context) {
        if (maxStubServicesOverride > 0) {
            return maxStubServicesOverride;
        }
        return discoverStubServiceCount(context);
    }

    private static int discoverStubServiceCount(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SERVICES);
            if (pi.services == null) {
                return FALLBACK_STUB_COUNT;
            }
            int maxIndex = -1;
            Pattern num = Pattern.compile("StubSandboxedProcessService(\\d+)$");
            for (ServiceInfo s : pi.services) {
                if (s.name == null) {
                    continue;
                }
                Matcher m = num.matcher(s.name);
                if (m.find()) {
                    maxIndex = Math.max(maxIndex, Integer.parseInt(m.group(1)));
                }
            }
            return maxIndex >= 0 ? maxIndex + 1 : FALLBACK_STUB_COUNT;
        } catch (Throwable ignored) {
            return FALLBACK_STUB_COUNT;
        }
    }
}
