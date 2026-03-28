package com.norman.webviewup.lib.hook;

import android.content.pm.PackageInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次 WebView 热替换所针对的包与本地文件快照，在 {@link com.norman.webviewup.lib.WebViewReplace}
 * 中组装后传给各 Hook，避免多处分歧的构造参数。
 * <p>
 * {@link #apkPath} / {@link #libsPath} 可同时为 null（仅已安装包路径）；非 null 时需成对出现以安装 PMS Hook。
 */
public final class ReplaceTarget {

    private final String webViewPackageName;
    @Nullable
    private final String apkPath;
    @Nullable
    private final String libsPath;

    private ReplaceTarget(@NonNull String webViewPackageName,
                          @Nullable String apkPath,
                          @Nullable String libsPath) {
        this.webViewPackageName = webViewPackageName;
        this.apkPath = apkPath;
        this.libsPath = libsPath;
    }

    /**
     * 主进程 {@link com.norman.webviewup.lib.WebViewReplace} 使用：与 {@link PackageInfo} 及路径参数对齐。
     */
    @NonNull
    public static ReplaceTarget from(@NonNull PackageInfo packageInfo,
                                     @Nullable String apkPath,
                                     @Nullable String libsPath) {
        return new ReplaceTarget(packageInfo.packageName, apkPath, libsPath);
    }

    /**
     * 沙盒子进程等场景：三者均非空。
     */
    @NonNull
    public static ReplaceTarget localPackage(@NonNull String packageName,
                                             @NonNull String apkPath,
                                             @NonNull String libsPath) {
        return new ReplaceTarget(packageName, apkPath, libsPath);
    }

    @NonNull
    public String getWebViewPackageName() {
        return webViewPackageName;
    }

    @Nullable
    public String getApkPath() {
        return apkPath;
    }

    @Nullable
    public String getLibsPath() {
        return libsPath;
    }

    /** 为 true 时可安装 {@link PackageManagerServiceHook} / {@link SandboxedProcessPackageManagerHook}。 */
    public boolean hasLocalApk() {
        return apkPath != null && libsPath != null;
    }
}
