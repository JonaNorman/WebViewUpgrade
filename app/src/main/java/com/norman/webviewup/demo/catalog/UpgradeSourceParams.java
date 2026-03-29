package com.norman.webviewup.demo.catalog;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.norman.webviewup.lib.source.UpgradeAssetSource;
import com.norman.webviewup.lib.source.UpgradeDownloadSource;
import com.norman.webviewup.lib.source.UpgradePackageSource;
import com.norman.webviewup.lib.source.UpgradeSource;

/**
 * Demo helper: immutable fields to rebuild a built-in {@link UpgradeSource} after cold start.
 * Host apps may copy or replace with their own persistence model.
 */
public final class UpgradeSourceParams {

    @NonNull
    private final UpgradeSourceKind kind;
    @NonNull
    private final String downloadUrl;
    @Nullable
    private final String assetName;
    @Nullable
    private final String installedPackageName;

    public UpgradeSourceParams(
            @NonNull UpgradeSourceKind kind,
            @Nullable String downloadUrl,
            @Nullable String assetName,
            @Nullable String installedPackageName) {
        this.kind = kind;
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
        this.assetName = assetName;
        this.installedPackageName = installedPackageName;
    }

    @NonNull
    public UpgradeSourceKind getKind() {
        return kind;
    }

    @NonNull
    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Nullable
    public String getAssetName() {
        return assetName;
    }

    @Nullable
    public String getInstalledPackageName() {
        return installedPackageName;
    }

    @Nullable
    public UpgradeSource toUpgradeSource(@NonNull Context context) {
        Context app = context.getApplicationContext();
        switch (kind) {
            case ASSET:
                if (TextUtils.isEmpty(assetName)) {
                    return null;
                }
                return new UpgradeAssetSource(app, assetName);
            case NETWORK:
                if (TextUtils.isEmpty(downloadUrl)) {
                    return null;
                }
                return new UpgradeDownloadSource(app, downloadUrl);
            case INSTALLED_PACKAGE:
                if (TextUtils.isEmpty(installedPackageName)) {
                    return null;
                }
                return new UpgradePackageSource(app, installedPackageName);
            default:
                return null;
        }
    }
}
