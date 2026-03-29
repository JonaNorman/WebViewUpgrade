package com.norman.webviewup.demo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class WebViewActivity extends Activity {

    /**
     * Order must match {@link R.array#wv_test_page_labels}.
     */
    private static final String[] TEST_URLS = {
            "https://m.baidu.com",
            "https://imgcache.qq.com/qcloud/cdn/official/h2test/index.html",
            "https://html5test.com/",
            "https://get.webgl.org/",
            "https://test-videos.co.uk/bigbuckbunny/mp4-h265",
    };

    private WebView mWebView;

    private TextView userAgentTextView;

    private String lastSpinnerUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        mWebView = findViewById(R.id.webview);
        DemoWebViewHolder.set(mWebView);
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
                    DemoWebViewHolder.set(null);
                    ((ViewGroup) view.getParent()).removeView(view);
                    view.destroy();
                    mWebView = null;
                }
                finish();
                return true;
            }
        });

        String[] labels = getResources().getStringArray(R.array.wv_test_page_labels);
        Spinner urlSpinner = findViewById(R.id.urlSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        urlSpinner.setAdapter(adapter);
        urlSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String url = TEST_URLS[position];
                if (url.equals(lastSpinnerUrl)) {
                    return;
                }
                lastSpinnerUrl = url;
                mWebView.clearHistory();
                mWebView.loadUrl(url);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        DemoWebViewHolder.set(null);
        if (mWebView != null) {
            try {
                mWebView.destroy();
            } catch (Throwable ignored) {
            }
            mWebView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

}
