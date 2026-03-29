package com.norman.webviewup.demo.catalog;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.norman.webviewup.demo.R;
import com.norman.webviewup.lib.source.UpgradeAssetSource;
import com.norman.webviewup.lib.source.UpgradeDownloadSource;
import com.norman.webviewup.lib.source.UpgradePackageSource;
import com.norman.webviewup.lib.source.UpgradeSource;

import android.content.Context;

/**
 * One row in the demo upgrade picker.
 */
public final class DemoUpgradeChoice {

    /** Sort: 内置 → 已安装(Chrome) → 在线未内置 → 需 prefetch */
    public static final int SECTION_ASSET = 0;
    public static final int SECTION_INSTALLED = 1;
    public static final int SECTION_NETWORK = 2;
    public static final int SECTION_PREFETCH = 3;

    public enum SourceKind {
        NETWORK,
        ASSET,
        INSTALLED_PACKAGE
    }

    /** Display tone for the status line (color). */
    public enum StatusTone {
        BUNDLED(R.color.wv_tone_bundled),
        DOWNLOADED(R.color.wv_tone_downloaded),
        INSTALLED(R.color.wv_tone_installed),
        NETWORK(R.color.wv_tone_network),
        PREFETCH(R.color.wv_tone_prefetch);

        @ColorRes
        public final int colorRes;

        StatusTone(@ColorRes int colorRes) {
            this.colorRes = colorRes;
        }
    }

    /** Row line 1 in dialog: vendor · ABI (keeps arch off the version wrap line). */
    @NonNull
    public final String lineVendorArch;
    /** Row line 2: shown after {@link #statusLabel} with a separator (version string). */
    @NonNull
    public final String lineVersion;
    /** Short localized tag: 内置 / 在线 / … */
    @NonNull
    public final String statusLabel;
    @NonNull
    public final StatusTone statusTone;
    public final int sectionOrder;
    @NonNull
    public final String packageName;
    @NonNull
    public final String versionLabel;
    @NonNull
    public final SourceKind sourceKind;
    @NonNull
    public final String downloadUrl;
    @Nullable
    public final String assetName;
    @Nullable
    public final String installedPackageName;

    public DemoUpgradeChoice(
            @NonNull String lineVendorArch,
            @NonNull String lineVersion,
            @NonNull String statusLabel,
            @NonNull StatusTone statusTone,
            int sectionOrder,
            @NonNull String packageName,
            @NonNull String versionLabel,
            @NonNull SourceKind sourceKind,
            @NonNull String downloadUrl,
            @Nullable String assetName,
            @Nullable String installedPackageName) {
        this.lineVendorArch = lineVendorArch;
        this.lineVersion = lineVersion;
        this.statusLabel = statusLabel;
        this.statusTone = statusTone;
        this.sectionOrder = sectionOrder;
        this.packageName = packageName;
        this.versionLabel = versionLabel;
        this.sourceKind = sourceKind;
        this.downloadUrl = downloadUrl;
        this.assetName = assetName;
        this.installedPackageName = installedPackageName;
    }

    @NonNull
    public String sortKey() {
        return lineVendorArch + '\u0001' + lineVersion;
    }

    @Nullable
    public UpgradeSource toUpgradeSource(@NonNull Context context) {
        switch (sourceKind) {
            case ASSET:
                if (assetName == null || assetName.isEmpty()) {
                    return null;
                }
                return new UpgradeAssetSource(context.getApplicationContext(), assetName);
            case NETWORK:
                if (downloadUrl == null || downloadUrl.isEmpty()) {
                    return null;
                }
                return new UpgradeDownloadSource(context.getApplicationContext(), downloadUrl);
            case INSTALLED_PACKAGE:
                if (installedPackageName == null || installedPackageName.isEmpty()) {
                    return null;
                }
                return new UpgradePackageSource(context.getApplicationContext(), installedPackageName);
            default:
                return null;
        }
    }
}
