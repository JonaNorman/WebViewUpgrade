package com.norman.webviewup.demo.catalog;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.norman.webviewup.demo.R;
import com.norman.webviewup.lib.util.ProcessUtils;
import com.norman.webviewup.lib.util.VersionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WebViewPackageCatalog {

    public static final String CHROME_PACKAGE = "com.android.chrome";

    private WebViewPackageCatalog() {
    }

    @NonNull
    public static List<DemoUpgradeChoice> buildCatalogChoices(
            @NonNull Context context,
            boolean preferGhProxy) throws Exception {
        String json = CatalogJsonLoader.loadCatalogJson(context);
        List<CatalogEntry> flat = WebViewPackagesParser.parse(json);
        String instruction = ProcessUtils.getCurrentInstruction();
        int sdk = Build.VERSION.SDK_INT;

        List<CatalogEntry> filtered = new ArrayList<>();
        for (CatalogEntry e : flat) {
            if (sdk < e.minApi) {
                continue;
            }
            if (!CatalogArch.matchesDevice(e.arch, instruction)) {
                continue;
            }
            filtered.add(e);
        }

        Collections.sort(filtered, new Comparator<CatalogEntry>() {
            @Override
            public int compare(CatalogEntry a, CatalogEntry b) {
                int c = a.vendorId.compareTo(b.vendorId);
                if (c != 0) {
                    return c;
                }
                int v = VersionUtils.compareVersion(b.version, a.version);
                if (v != 0) {
                    return v;
                }
                return a.arch.compareTo(b.arch);
            }
        });

        Map<String, AssetManifestEntry> manifest = WebViewAssetManifest.load(context);
        List<DemoUpgradeChoice> choices = new ArrayList<>();
        for (CatalogEntry e : filtered) {
            String key = AssetManifestEntry.key(e.vendorId, e.version, e.arch);
            AssetManifestEntry am = manifest.get(key);
            String url = e.resolveDownloadUrl(preferGhProxy);
            String lineVendorArch = vendorArchLine(context, e.vendorId, e.arch);

            if (am != null) {
                boolean fileOk = WebViewAssetManifest.assetFileExists(context, am.assetName);
                String pkg = !TextUtils.isEmpty(am.packageName) ? am.packageName : e.packageName;
                if (fileOk) {
                    choices.add(new DemoUpgradeChoice(
                            lineVendorArch,
                            e.version,
                            context.getString(R.string.wv_status_bundled),
                            DemoUpgradeChoice.StatusTone.BUNDLED,
                            DemoUpgradeChoice.SECTION_ASSET,
                            pkg,
                            e.version,
                            DemoUpgradeChoice.SourceKind.ASSET,
                            url,
                            am.assetName,
                            null));
                } else {
                    boolean cached = UpgradeDownloadCache.hasCachedApkForUrl(context, url);
                    choices.add(new DemoUpgradeChoice(
                            lineVendorArch,
                            e.version,
                            context.getString(cached ? R.string.wv_status_downloaded : R.string.wv_status_prefetch),
                            cached ? DemoUpgradeChoice.StatusTone.DOWNLOADED : DemoUpgradeChoice.StatusTone.PREFETCH,
                            DemoUpgradeChoice.SECTION_PREFETCH,
                            pkg,
                            e.version,
                            DemoUpgradeChoice.SourceKind.NETWORK,
                            url,
                            null,
                            null));
                }
            } else {
                boolean cached = UpgradeDownloadCache.hasCachedApkForUrl(context, url);
                choices.add(new DemoUpgradeChoice(
                        lineVendorArch,
                        e.version,
                        context.getString(cached ? R.string.wv_status_downloaded : R.string.wv_status_online),
                        cached ? DemoUpgradeChoice.StatusTone.DOWNLOADED : DemoUpgradeChoice.StatusTone.NETWORK,
                        DemoUpgradeChoice.SECTION_NETWORK,
                        e.packageName,
                        e.version,
                        DemoUpgradeChoice.SourceKind.NETWORK,
                        url,
                        null,
                        null));
            }
        }
        sortDemoChoices(choices);
        return choices;
    }

    public static void sortDemoChoices(@NonNull List<DemoUpgradeChoice> choices) {
        Collections.sort(choices, new Comparator<DemoUpgradeChoice>() {
            @Override
            public int compare(DemoUpgradeChoice a, DemoUpgradeChoice b) {
                int c = Integer.compare(a.sectionOrder, b.sectionOrder);
                if (c != 0) {
                    return c;
                }
                return a.sortKey().compareTo(b.sortKey());
            }
        });
    }

    @NonNull
    private static String vendorArchLine(@NonNull Context context, @NonNull String vendorId, @NonNull String arch) {
        return vendorDisplayName(context, vendorId) + " · " + arch;
    }

    @NonNull
    private static String vendorDisplayName(@NonNull Context context, @NonNull String vendorId) {
        switch (vendorId) {
            case "android":
                return context.getString(R.string.wv_vendor_android);
            case "google":
                return context.getString(R.string.wv_vendor_google);
            case "chrome":
                return context.getString(R.string.wv_vendor_chrome);
            case "huawei":
                return context.getString(R.string.wv_vendor_huawei);
            case "amazon":
                return context.getString(R.string.wv_vendor_amazon);
            default:
                if (vendorId.isEmpty()) {
                    return context.getString(R.string.wv_vendor_unknown);
                }
                return Character.toUpperCase(vendorId.charAt(0)) + vendorId.substring(1);
        }
    }

    @NonNull
    public static List<DemoUpgradeChoice> appendInstalledChrome(
            @NonNull Context context,
            @NonNull List<DemoUpgradeChoice> base,
            @NonNull android.content.pm.PackageManager pm) {
        List<DemoUpgradeChoice> out = new ArrayList<>(base);
        try {
            android.content.pm.PackageInfo info = pm.getPackageInfo(CHROME_PACKAGE, 0);
            String ver = info.versionName != null ? info.versionName : String.valueOf(info.versionCode);
            out.add(new DemoUpgradeChoice(
                    context.getString(R.string.wv_vendor_chrome),
                    ver,
                    context.getString(R.string.wv_status_installed),
                    DemoUpgradeChoice.StatusTone.INSTALLED,
                    DemoUpgradeChoice.SECTION_INSTALLED,
                    CHROME_PACKAGE,
                    ver,
                    DemoUpgradeChoice.SourceKind.INSTALLED_PACKAGE,
                    "",
                    null,
                    CHROME_PACKAGE));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // omit
        }
        sortDemoChoices(out);
        return out;
    }
}
