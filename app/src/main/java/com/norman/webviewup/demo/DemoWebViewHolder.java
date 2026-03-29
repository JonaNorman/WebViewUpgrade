package com.norman.webviewup.demo;

import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Nullable;

/**
 * Holds the active {@link WebView} from {@link WebViewActivity} so we can destroy it before
 * a hard process restart, reducing stray renderer / sandbox bindings.
 */
public final class DemoWebViewHolder {

    @Nullable
    private static volatile WebView sWebView;

    private DemoWebViewHolder() {
    }

    public static void set(@Nullable WebView webView) {
        sWebView = webView;
    }

    public static void destroyHeldWebView() {
        WebView w = sWebView;
        sWebView = null;
        if (w == null) {
            return;
        }
        try {
            ViewGroup parent = (ViewGroup) w.getParent();
            if (parent != null) {
                parent.removeView(w);
            }
            w.destroy();
        } catch (Throwable ignored) {
        }
    }
}
