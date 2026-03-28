package com.norman.webviewup.demo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;


public class WebViewActivity extends Activity {

    private WebView mWebView;

    private TextView userAgentTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        mWebView = findViewById(R.id.webview);
        userAgentTextView = findViewById(R.id.userAgentTextView);
        WebSettings webSettings = mWebView.getSettings();
        userAgentTextView.setText(webSettings.getUserAgentString());
        webSettings.setJavaScriptEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // 拦截渲染进程崩溃，避免主进程闪退
                Log.e("WebViewActivity", "RenderProcessGone! Did crash: " + detail.didCrash());
                Toast.makeText(WebViewActivity.this, "WebView渲染进程已崩溃，请重试或检查内核兼容性", Toast.LENGTH_LONG).show();
                // 必须在崩溃后销毁当前异常的WebView实例或退出当前页面
                if (view != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                    view.destroy();
                }
                finish();
                return true; 
            }
        });
        mWebView.loadUrl("https://test-videos.co.uk/bigbuckbunny/mp4-h265");
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

}