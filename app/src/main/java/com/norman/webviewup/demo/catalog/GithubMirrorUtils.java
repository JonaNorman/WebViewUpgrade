package com.norman.webviewup.demo.catalog;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Locale;

/** Demo：GitHub 系直链统一经 ghfast 前缀加速，其它 host 原样返回。 */
public final class GithubMirrorUtils {

    /**
     * Demo 写死的加速前缀（前缀 + 完整原始 URL），与常见 gh-proxy 用法一致。
     *
     * @see <a href="https://ghfast.top/">ghfast.top</a>
     */
    public static final String DEMO_MIRROR_PREFIX = "https://ghfast.top/";

    private GithubMirrorUtils() {
    }

    public static boolean isGithubHostedUrl(@NonNull String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.US);
            return host.contains("github.com") || host.contains("githubusercontent.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    @NonNull
    public static String applyDemoMirror(@NonNull String originalUrl) {
        if (TextUtils.isEmpty(originalUrl)) {
            return "";
        }
        if (!isGithubHostedUrl(originalUrl)) {
            return originalUrl;
        }
        String p = DEMO_MIRROR_PREFIX;
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        return p + originalUrl;
    }
}
