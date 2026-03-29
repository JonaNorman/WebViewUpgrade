package com.norman.webviewup.demo.catalog;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads {@code webview_packages.json} from assets (produced at build time under
 * {@code build/generated/webview-assets} via {@code syncWebViewCatalog}).
 */
public final class CatalogJsonLoader {

    public static final String ASSET_NAME = "webview_packages.json";

    private CatalogJsonLoader() {
    }

    @NonNull
    public static String loadCatalogJson(@NonNull Context context) throws Exception {
        return readAssetUtf8(context, ASSET_NAME);
    }

    @NonNull
    private static String readAssetUtf8(@NonNull Context context, @NonNull String assetName) throws Exception {
        InputStream in = context.getAssets().open(assetName);
        try {
            return readStreamUtf8(in);
        } finally {
            in.close();
        }
    }

    @NonNull
    private static String readStreamUtf8(@NonNull InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8.name());
    }
}
