package com.norman.webviewup.demo.catalog;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;

/**
 * Mirrors {@link com.norman.webviewup.lib.source.UpgradeDownloadSource} sandbox layout:
 * {@code getDir("package_webview") / md5(url) / base.apk}.
 */
public final class UpgradeDownloadCache {

    private UpgradeDownloadCache() {
    }

    public static boolean hasCachedApkForUrl(@NonNull Context context, String downloadUrl) {
        if (TextUtils.isEmpty(downloadUrl)) {
            return false;
        }
        File dir = context.getDir("package_webview", Context.MODE_PRIVATE);
        File workspace = new File(dir, FileUtils.md5(downloadUrl));
        File apk = new File(workspace, "base.apk");
        return FileUtils.isNotEmpty(apk.getAbsolutePath());
    }
}
