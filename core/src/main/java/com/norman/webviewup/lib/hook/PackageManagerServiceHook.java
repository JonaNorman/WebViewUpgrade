package com.norman.webviewup.lib.hook;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 主进程专用：在 {@link com.norman.webviewup.lib.WebViewReplace} 中安装，
 * 与沙盒子进程使用的 {@link SandboxedProcessPackageManagerHook} 分离，避免 Context / 生命周期语义混淆。
 */
public class PackageManagerServiceHook extends AbstractWebViewPackageManagerHook {

    public PackageManagerServiceHook(@NonNull Context context, @NonNull ReplaceTarget target) {
        super(context,
                Objects.requireNonNull(target.getWebViewPackageName(), "webViewPackageName"),
                Objects.requireNonNull(target.getApkPath(), "apkPath"),
                Objects.requireNonNull(target.getLibsPath(), "libsPath"));
    }
}
