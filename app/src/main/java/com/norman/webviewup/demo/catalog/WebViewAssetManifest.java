package com.norman.webviewup.demo.catalog;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public final class WebViewAssetManifest {

    public static final String ASSET_NAME = "webview_asset_manifest.json";

    private WebViewAssetManifest() {
    }

    @NonNull
    public static Map<String, AssetManifestEntry> load(@NonNull Context context) {
        try {
            AssetManager am = context.getAssets();
            InputStream in = am.open(ASSET_NAME);
            try {
                String json = readUtf8(in);
                return parse(json);
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public static boolean assetFileExists(@NonNull Context context, @NonNull String assetName) {
        if (assetName.isEmpty()) {
            return false;
        }
        try {
            InputStream in = context.getAssets().open(assetName);
            in.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static String readUtf8(@NonNull InputStream in) {
        Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @NonNull
    static Map<String, AssetManifestEntry> parse(@NonNull String json) throws Exception {
        Map<String, AssetManifestEntry> map = new HashMap<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String vendor = o.optString("vendor", "");
            String version = o.optString("version", "");
            String arch = o.optString("arch", "");
            String assetName = o.optString("assetName", "");
            String pkg = o.optString("packageName", null);
            if (vendor.isEmpty() || version.isEmpty() || arch.isEmpty() || assetName.isEmpty()) {
                continue;
            }
            AssetManifestEntry e = new AssetManifestEntry(vendor, version, arch, assetName, pkg);
            map.put(e.key(), e);
        }
        return map;
    }
}
