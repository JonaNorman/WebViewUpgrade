package com.norman.webviewup.lib.hook;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@code bindIsolatedService} / {@code bindServiceInstance} 的参数数组适配为当前设备上
 * {@link android.app.IActivityManager#bindService} 的形参。
 * <p>
 * 优先用「Intent 位置 + flags/userId 几何关系」删多余槽；失败则回退到原启发式循环删除。
 */
final class BindServiceArgsAdapter {

    private BindServiceArgsAdapter() {
    }

    /**
     * 在 IActivityManager 实现上选取最匹配的 {@code bindService} 重载。
     */
    @Nullable
    static Method findBestBindServiceMethod(@NonNull Object mOriginalAm, @Nullable Object[] srcArgs) {
        int srcLen = srcArgs == null ? 0 : srcArgs.length;
        List<Method> candidates = new ArrayList<>();
        try {
            Class<?> amClass = mOriginalAm.getClass();
            for (Class<?> iface : amClass.getInterfaces()) {
                for (Method m : iface.getDeclaredMethods()) {
                    if ("bindService".equals(m.getName())) {
                        candidates.add(m);
                    }
                }
            }
            if (candidates.isEmpty()) {
                for (Method m : amClass.getMethods()) {
                    if ("bindService".equals(m.getName())) {
                        candidates.add(m);
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Method best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Method m : candidates) {
            int pc = m.getParameterCount();
            if (pc > srcLen) {
                continue;
            }
            int gap = srcLen - pc;
            if (gap > 3) {
                continue;
            }
            int score = scorePrefixMatch(m.getParameterTypes(), srcArgs);
            if (gap <= 2) {
                score += 20;
            }
            if (gap == 0) {
                score += 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best != null ? best : candidates.get(0);
    }

    private static int scorePrefixMatch(Class<?>[] targetTypes, Object[] srcArgs) {
        if (srcArgs == null) {
            return 0;
        }
        int score = 0;
        int n = Math.min(targetTypes.length, srcArgs.length);
        for (int i = 0; i < n; i++) {
            Object arg = srcArgs[i];
            if (arg == null) {
                continue;
            }
            Class<?> t = targetTypes[i];
            Class<?> argClass = arg.getClass();
            if (t.isAssignableFrom(argClass)) {
                score += 4;
            } else if (t.isPrimitive()) {
                if (t == int.class && arg instanceof Integer) {
                    score += 4;
                } else if (t == long.class && arg instanceof Long) {
                    score += 4;
                } else if (t == boolean.class && arg instanceof Boolean) {
                    score += 4;
                }
            } else if ((t == Integer.class || t == Long.class) && arg instanceof Number) {
                score += 2;
            }
        }
        return score;
    }

    @NonNull
    static Object[] adapt(@Nullable Object[] srcArgs,
                          @NonNull Method bindServiceMethod,
                          @Nullable String hostPackageName) {
        int targetParamCount = bindServiceMethod.getParameterCount();
        Class<?>[] targetTypes = bindServiceMethod.getParameterTypes();
        List<Object> mutable = new ArrayList<>();
        if (srcArgs != null) {
            for (Object arg : srcArgs) {
                mutable.add(arg);
            }
        }

        if (!trimToTargetCount(mutable, targetParamCount, hostPackageName)) {
            legacyTrimToTargetCount(mutable, targetParamCount, hostPackageName);
        }

        Object[] result = new Object[targetParamCount];
        for (int i = 0; i < targetParamCount; i++) {
            Object value = i < mutable.size() ? mutable.get(i) : null;
            result[i] = coerceValueForType(value, targetTypes[i]);
        }

        clearExternalServiceFlag(result, targetTypes);
        return result;
    }

    /**
     * 基于 Intent / flags / userId 相对位置删参；成功返回 true（size 已等于 targetCount）。
     */
    private static boolean trimToTargetCount(List<Object> mutable,
                                             int targetCount,
                                             @Nullable String hostPackageName) {
        while (mutable.size() > targetCount) {
            int diff = mutable.size() - targetCount;
            int intentIdx = indexOfFirstIntent(mutable);
            if (intentIdx < 0) {
                return false;
            }
            int flagsIdx = findFirstFlagsIndexAfter(mutable, intentIdx);
            int userIdIdx = findLastIntegerIndex(mutable);
            if (diff == 1 && flagsIdx > intentIdx && userIdIdx > flagsIdx) {
                int remove = pickExtraSlotBetween(mutable, flagsIdx, userIdIdx, hostPackageName);
                if (remove >= 0) {
                    mutable.remove(remove);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        return mutable.size() == targetCount;
    }

    private static void legacyTrimToTargetCount(List<Object> mutable,
                                                int targetCount,
                                                @Nullable String hostPackageName) {
        while (mutable.size() > targetCount) {
            int removeIndex = findInstanceNameIndexLegacy(mutable, hostPackageName);
            if (removeIndex < 0) {
                removeIndex = findNullableGapIndexLegacy(mutable);
            }
            if (removeIndex < 0) {
                int flagsIndex = findFirstFlagsIndexWholeList(mutable);
                removeIndex = (flagsIndex >= 0 && flagsIndex + 1 < mutable.size())
                        ? flagsIndex + 1
                        : mutable.size() - 1;
            }
            mutable.remove(removeIndex);
        }
    }

    private static int indexOfFirstIntent(List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof Intent) {
                return i;
            }
        }
        return -1;
    }

    /** flags：在 Intent 之后第一个 int/long（AOSP bindIsolated 语义下即为 service flags）。 */
    private static int findFirstFlagsIndexAfter(List<Object> args, int intentIdx) {
        int start = intentIdx >= 0 ? intentIdx + 1 : 0;
        for (int i = start; i < args.size(); i++) {
            Object v = args.get(i);
            if (v instanceof Integer || v instanceof Long) {
                return i;
            }
        }
        return -1;
    }

    private static int findFirstFlagsIndexWholeList(List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            Object v = args.get(i);
            if (v instanceof Integer || v instanceof Long) {
                return i;
            }
        }
        return -1;
    }

    private static int findLastIntegerIndex(List<Object> args) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (args.get(i) instanceof Integer) {
                return i;
            }
        }
        return -1;
    }

    private static int pickExtraSlotBetween(List<Object> args,
                                            int flagsIdx,
                                            int userIdIdx,
                                            @Nullable String hostPackageName) {
        for (int i = flagsIdx + 1; i < userIdIdx; i++) {
            Object v = args.get(i);
            if (v == null) {
                return i;
            }
            if (v instanceof String) {
                String s = (String) v;
                if (hostPackageName == null || !hostPackageName.equals(s)) {
                    return i;
                }
            }
        }
        for (int i = flagsIdx + 1; i < userIdIdx; i++) {
            if (args.get(i) instanceof String) {
                return i;
            }
        }
        return -1;
    }

    private static int findInstanceNameIndexLegacy(List<Object> args, @Nullable String hostPackageName) {
        int flagsIndex = findFirstFlagsIndexWholeList(args);
        int userIdIndex = findLastIntegerIndex(args);
        if (flagsIndex < 0 || userIdIndex <= flagsIndex) {
            return -1;
        }
        for (int i = flagsIndex + 1; i < userIdIndex; i++) {
            Object value = args.get(i);
            if (!(value instanceof String)) {
                continue;
            }
            String text = (String) value;
            if (text != null && hostPackageName != null && text.equals(hostPackageName)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int findNullableGapIndexLegacy(List<Object> args) {
        int flagsIndex = findFirstFlagsIndexWholeList(args);
        int userIdIndex = findLastIntegerIndex(args);
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

    private static Object coerceValueForType(Object value, Class<?> targetType) {
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

    static void clearExternalServiceFlag(Object[] args, Class<?>[] types) {
        long mask = 0x00800000L | 0x80000000L;
        try {
            mask |= Context.BIND_EXTERNAL_SERVICE;
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) {
                int flags = (int) args[i];
                int m = (int) (mask & 0xFFFFFFFFL);
                args[i] = flags & ~m;
                return;
            } else if (args[i] instanceof Long) {
                long flags = (long) args[i];
                args[i] = flags & ~mask;
                return;
            }
        }
    }
}
