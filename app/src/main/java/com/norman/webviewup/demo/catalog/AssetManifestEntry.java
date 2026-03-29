package com.norman.webviewup.demo.catalog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AssetManifestEntry {

    @NonNull
    public final String vendorId;
    @NonNull
    public final String version;
    @NonNull
    public final String arch;
    @NonNull
    public final String assetName;
    @Nullable
    public final String packageName;

    public AssetManifestEntry(
            @NonNull String vendorId,
            @NonNull String version,
            @NonNull String arch,
            @NonNull String assetName,
            @Nullable String packageName) {
        this.vendorId = vendorId;
        this.version = version;
        this.arch = arch;
        this.assetName = assetName;
        this.packageName = packageName;
    }

    @NonNull
    public static String key(
            @NonNull String vendorId,
            @NonNull String version,
            @NonNull String arch) {
        return vendorId + "|" + version + "|" + arch;
    }

    @NonNull
    public String key() {
        return key(vendorId, version, arch);
    }
}
