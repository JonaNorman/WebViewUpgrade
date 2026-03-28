package com.norman.webviewup.lib.hook;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 仅用于 {@code :sandboxed_processN} 子进程：在 Stub 服务 {@code onBind} 时安装，
 * 使 {@link Context#createPackageContext(String, int)} 能解析到热替换 WebView APK，
 * 从而获得系统创建的 {@code LoadedApk} / linker namespace（修复 ICU 等 native 初始化）。
 * <p>
 * 逻辑与 {@link PackageManagerServiceHook} 相同，拆类仅区分使用场景，便于维护。
 */
public class SandboxedProcessPackageManagerHook extends AbstractWebViewPackageManagerHook {

    public SandboxedProcessPackageManagerHook(@NonNull Context context, @NonNull ReplaceTarget target) {
        super(context,
                Objects.requireNonNull(target.getWebViewPackageName(), "webViewPackageName"),
                Objects.requireNonNull(target.getApkPath(), "apkPath"),
                Objects.requireNonNull(target.getLibsPath(), "libsPath"));
    }
}
