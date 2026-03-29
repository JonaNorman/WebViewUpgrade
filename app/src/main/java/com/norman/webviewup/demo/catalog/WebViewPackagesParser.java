package com.norman.webviewup.demo.catalog;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class WebViewPackagesParser {

    private WebViewPackagesParser() {
    }

    @NonNull
    public static List<CatalogEntry> parse(@NonNull String json) throws Exception {
        List<CatalogEntry> out = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray vendors = root.optJSONArray("vendors");
        if (vendors == null) {
            return out;
        }
        for (int i = 0; i < vendors.length(); i++) {
            JSONObject v = vendors.getJSONObject(i);
            String vendorId = v.optString("vendor", "");
            String vendorName = v.optString("name", vendorId);
            JSONArray packageNames = v.optJSONArray("package_names");
            String primaryPackage = "";
            if (packageNames != null && packageNames.length() > 0) {
                primaryPackage = packageNames.optString(0, "");
            }
            JSONArray packages = v.optJSONArray("packages");
            if (packages == null) {
                continue;
            }
            for (int j = 0; j < packages.length(); j++) {
                JSONObject p = packages.getJSONObject(j);
                String version = p.optString("version", "");
                int minApi = p.optInt("min_api", 0);
                String arch = p.optString("arch", "");
                String url = p.optString("url", "");
                String gh = p.optString("ghproxy_url", "");
                out.add(new CatalogEntry(
                        vendorId,
                        vendorName,
                        primaryPackage,
                        version,
                        minApi,
                        arch,
                        url,
                        gh));
            }
        }
        return out;
    }
}
