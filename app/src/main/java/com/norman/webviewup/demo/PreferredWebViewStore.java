package com.norman.webviewup.demo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.norman.webviewup.demo.catalog.DemoUpgradeChoice;
import com.norman.webviewup.demo.catalog.UpgradeSourceKind;
import com.norman.webviewup.demo.catalog.UpgradeSourceParams;
import com.norman.webviewup.lib.source.UpgradeSource;

/**
 * Persists the demo's chosen WebView package spec so cold start can call
 * {@link com.norman.webviewup.lib.WebViewUpgrade#upgrade} before any WebView initializes.
 */
public final class PreferredWebViewStore {

    private static final String PREFS = "demo_preferred_webview";
    private static final String KEY_SOURCE_KIND = "source_kind";
    private static final String KEY_DOWNLOAD_URL = "download_url";
    private static final String KEY_ASSET_NAME = "asset_name";
    private static final String KEY_INSTALLED_PACKAGE = "installed_package_name";
    private static final String KEY_DISPLAY_PACKAGE = "display_package_name";
    private static final String KEY_DISPLAY_VERSION = "display_version";

    private PreferredWebViewStore() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasChoice(@NonNull Context ctx) {
        return prefs(ctx).contains(KEY_SOURCE_KIND);
    }

    public static void save(@NonNull Context ctx, @NonNull DemoUpgradeChoice choice) {
        UpgradeSourceParams p = choice.toUpgradeSourceParams();
        SharedPreferences.Editor e = prefs(ctx).edit();
        e.putString(KEY_SOURCE_KIND, p.getKind().name());
        e.putString(KEY_DOWNLOAD_URL, p.getDownloadUrl());
        e.putString(KEY_ASSET_NAME, p.getAssetName() != null ? p.getAssetName() : "");
        e.putString(KEY_INSTALLED_PACKAGE,
                p.getInstalledPackageName() != null ? p.getInstalledPackageName() : "");
        e.putString(KEY_DISPLAY_PACKAGE, choice.packageName);
        e.putString(KEY_DISPLAY_VERSION, choice.versionLabel);
        e.apply();
    }

    public static void clear(@NonNull Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    @Nullable
    public static String getDisplayPackageName(@NonNull Context ctx) {
        return prefs(ctx).getString(KEY_DISPLAY_PACKAGE, null);
    }

    @Nullable
    public static String getDisplayVersion(@NonNull Context ctx) {
        return prefs(ctx).getString(KEY_DISPLAY_VERSION, null);
    }

    @Nullable
    public static UpgradeSource toUpgradeSource(@NonNull Context ctx) {
        SharedPreferences p = prefs(ctx);
        String kindStr = p.getString(KEY_SOURCE_KIND, null);
        if (kindStr == null) {
            return null;
        }
        UpgradeSourceKind kind;
        try {
            kind = UpgradeSourceKind.valueOf(kindStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String downloadUrl = p.getString(KEY_DOWNLOAD_URL, "");
        String assetName = p.getString(KEY_ASSET_NAME, "");
        String installed = p.getString(KEY_INSTALLED_PACKAGE, "");
        String assetOrNull = (assetName != null && !assetName.isEmpty()) ? assetName : null;
        String installedOrNull = (installed != null && !installed.isEmpty()) ? installed : null;
        UpgradeSourceParams params = new UpgradeSourceParams(
                kind,
                downloadUrl != null ? downloadUrl : "",
                assetOrNull,
                installedOrNull);
        return params.toUpgradeSource(ctx);
    }
}
