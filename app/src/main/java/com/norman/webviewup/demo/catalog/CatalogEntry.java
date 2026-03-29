package com.norman.webviewup.demo.catalog;

import androidx.annotation.NonNull;

/**
 * One flattened row from {@code webview_packages.json}.
 */
public final class CatalogEntry {

    @NonNull
    public final String vendorId;
    @NonNull
    public final String vendorDisplayName;
    @NonNull
    public final String packageName;
    @NonNull
    public final String version;
    public final int minApi;
    @NonNull
    public final String arch;
    @NonNull
    public final String downloadUrl;

    public CatalogEntry(
            @NonNull String vendorId,
            @NonNull String vendorDisplayName,
            @NonNull String packageName,
            @NonNull String version,
            int minApi,
            @NonNull String arch,
            @NonNull String downloadUrl) {
        this.vendorId = vendorId;
        this.vendorDisplayName = vendorDisplayName;
        this.packageName = packageName;
        this.version = version;
        this.minApi = minApi;
        this.arch = arch;
        this.downloadUrl = downloadUrl;
    }

    @NonNull
    public String manifestKey() {
        return vendorId + "|" + version + "|" + arch;
    }
}
