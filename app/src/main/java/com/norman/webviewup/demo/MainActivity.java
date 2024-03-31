package com.norman.webviewup.demo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.norman.webviewup.lib.UpgradeCallback;
import com.norman.webviewup.lib.WebViewUpgrade;
import com.norman.webviewup.lib.source.UpgradeAssetSource;
import com.norman.webviewup.lib.source.UpgradePackageSource;
import com.norman.webviewup.lib.source.UpgradeSource;
import com.norman.webviewup.lib.source.download.UpgradeDownloadSource;
import com.norman.webviewup.lib.util.ProcessUtils;
import com.norman.webviewup.lib.util.VersionUtils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends Activity implements UpgradeCallback {


    private static final Map<String, List<UpgradeInfo>> UPGRADE_PACKAGE_MAP = new HashMap<>();

    static {
        UPGRADE_PACKAGE_MAP.put("arm", Arrays.asList(
                new UpgradeInfo(
                        "com.google.android.webview",
                        "122.0.6261.64",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.google.android.webview_122.0.6261.64_armeabi-v7a.zip",
                        "网络"),
                new UpgradeInfo(
                        "com.android.webview",
                        "113.0.5672.136",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.android.webview_113.0.5672.13_armeabi-v7a.zip",
                        "网络"),
                new UpgradeInfo(
                        "com.huawei.webview",
                        "14.0.0.331",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.huawei.webview_14.0.0.331_arm64-v8a_armeabi-v7a.zip",
                        "网络"),
                new UpgradeInfo(
                        "com.android.chrome",
                        "122.0.6261.43",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.android.chrome_122.0.6261.64_armeabi-v7a.zip",
                        "网络"),

                new UpgradeInfo("com.amazon.webview.chromium",
                        "118-5993-tv.5993.155.51",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.amazon.webview.chromium_118-5993-tv.5993.155.51_armeabi-v7a.zip",
                        "网络"),

                new UpgradeInfo("com.amazon.webview.chromium",
                        "118-5993-tv.5993.155.51",
                        "com.webview.chromium_118-5993-tv.5993.155.51_armeabi-v7a.apk",
                        "内置"),
                new UpgradeInfo(
                        "com.android.chrome",
                        "122.0.6261.43",
                        "",
                        "安装包")


        ));

        UPGRADE_PACKAGE_MAP.put("arm64", Arrays.asList(
                new UpgradeInfo(
                        "com.huawei.webview",
                        "14.0.0.331",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.huawei.webview_14.0.0.331_arm64-v8a_armeabi-v7a.zip",
                        "网络")
        ));

        UPGRADE_PACKAGE_MAP.put("x86", Arrays.asList(
                new UpgradeInfo(
                        "com.google.android.webview",
                        "122.0.6261.64",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.google.android.webview_122.0.6261.64_x86.zip",
                        "网络")
        ));

    }


    ProgressBar progressBar;
    TextView systemWebViewPackageTextView;
    TextView upgradeWebViewPackageTextView;

    TextView upgradeStatusTextView;
    TextView upgradeErrorTextView;

    TextView upgradeProgressTextView;

    UpgradeInfo selectUpgradeInfo;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebViewUpgrade.addUpgradeCallback(this);
        progressBar = findViewById(R.id.upgradeProgressBar);
        systemWebViewPackageTextView = findViewById(R.id.systemWebViewPackageTextView);
        upgradeWebViewPackageTextView = findViewById(R.id.upgradeWebViewPackageTextView);
        upgradeStatusTextView = findViewById(R.id.upgradeStatusTextView);
        upgradeErrorTextView = findViewById(R.id.upgradeErrorTextView);
        upgradeProgressTextView = findViewById(R.id.upgradeProgressTextView);
        updateSystemWebViewPackageInfo();
        updateSystemWebViewPackageInfo();
        updateUpgradeWebViewStatus();


        findViewById(R.id.upgradeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChooseWebViewDialog();
            }
        });

        findViewById(R.id.webViewButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WebViewActivity.class));
            }
        });


    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebViewUpgrade.removeUpgradeCallback(this);
    }

    @Override
    public void onUpgradeProcess(float percent) {
        updateUpgradeWebViewStatus();
    }

    @Override
    public void onUpgradeComplete() {
        updateUpgradeWebViewStatus();
        Toast.makeText(getApplicationContext(), "webView upgrade success", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgradeError(Throwable throwable) {
        Toast.makeText(getApplicationContext(), "webView upgrade fail", Toast.LENGTH_SHORT).show();
        Log.e("MainActivity", "message:" + throwable.getMessage() + "\nstackTrace:" + Log.getStackTraceString(throwable));
        updateUpgradeWebViewStatus();
    }

    private void showChooseWebViewDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose WebView");
        List<UpgradeInfo> upgradeInfoList = UPGRADE_PACKAGE_MAP.get(ProcessUtils.getCurrentInstruction());

        String[] items = new String[upgradeInfoList.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = upgradeInfoList.get(i).title;
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (WebViewUpgrade.isProcessing()) {
                    Toast.makeText(getApplicationContext(), "webView is being upgraded, please wait", Toast.LENGTH_LONG).show();
                } else if (WebViewUpgrade.isCompleted()) {
                    Toast.makeText(getApplicationContext(), "webView is already upgrade success,not support dynamic switch", Toast.LENGTH_LONG).show();
                } else {
                    UpgradeInfo upgradeInfo = upgradeInfoList.get(which);
                    String  systemWebViewPackageName = WebViewUpgrade.getSystemWebViewPackageName();
                    if (systemWebViewPackageName != null &&systemWebViewPackageName.equals(upgradeInfo.packageName)
                            && VersionUtils.compareVersion( WebViewUpgrade.getSystemWebViewPackageVersion(),upgradeInfo.versionName) >= 0) {
                        Toast.makeText(getApplicationContext(), "system webView is larger than the one to be upgraded, so there is no need to upgrade", Toast.LENGTH_LONG).show();
                        return;
                    }
                    selectUpgradeInfo = upgradeInfo;
                    UpgradeSource upgradeSource = getUpgradeSource(upgradeInfo);
                    if (upgradeSource == null) {
                        return;
                    }
                    WebViewUpgrade.upgrade(upgradeSource);
                    updateUpgradeWebViewPackageInfo();
                    updateUpgradeWebViewStatus();
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Nullable
    private UpgradeSource getUpgradeSource(UpgradeInfo upgradeInfo) {
        UpgradeSource upgradeSource = null;
        if (upgradeInfo.extraInfo.equals("网络")) {
            upgradeSource = new UpgradeDownloadSource(
                    getApplicationContext(),
                    upgradeInfo.url,
                    new File(getApplicationContext().getFilesDir(), upgradeInfo.packageName + "/" + upgradeInfo.versionName + ".apk")
            );
        } else if (upgradeInfo.extraInfo.equals("内置")) {
            upgradeSource = new UpgradeAssetSource(
                    getApplicationContext(),
                    upgradeInfo.url,
                    new File(getApplicationContext().getFilesDir(), upgradeInfo.packageName + "/" + upgradeInfo.versionName + ".apk")
            );
        } else if (upgradeInfo.extraInfo.equals("安装包")) {
            upgradeSource = new UpgradePackageSource(
                    getApplicationContext(),
                    upgradeInfo.packageName
            );
        }
        return upgradeSource;
    }


    private void updateSystemWebViewPackageInfo() {
        String systemWebViewPackageName = WebViewUpgrade.getSystemWebViewPackageName();
        String systemWebViewPackageVersion = WebViewUpgrade.getSystemWebViewPackageVersion();

        String systemWebViewPackageInfo = "unknown";
        if (!TextUtils.isEmpty(systemWebViewPackageName)
                || !TextUtils.isEmpty(systemWebViewPackageVersion)) {
            systemWebViewPackageInfo = (!TextUtils.isEmpty(systemWebViewPackageName) ? systemWebViewPackageName : "unknown")
                    + ":" + (!TextUtils.isEmpty(systemWebViewPackageVersion) ? systemWebViewPackageVersion : "unknown");
        }
        systemWebViewPackageTextView.setText(systemWebViewPackageInfo);
    }

    private void updateUpgradeWebViewPackageInfo() {
        String upgradeWebViewPackageName = selectUpgradeInfo != null ? selectUpgradeInfo.packageName : null;
        String upgradeWebViewPackageVersion = selectUpgradeInfo != null ? selectUpgradeInfo.versionName : null;

        String upgradeWebViewPackageInfo = "";
        if (!TextUtils.isEmpty(upgradeWebViewPackageName)
                || !TextUtils.isEmpty(upgradeWebViewPackageVersion)) {
            upgradeWebViewPackageInfo = (!TextUtils.isEmpty(upgradeWebViewPackageName) ? upgradeWebViewPackageName : "unknown")
                    + ":" + (!TextUtils.isEmpty(upgradeWebViewPackageVersion) ? upgradeWebViewPackageVersion : "unknown");
        } else {
            upgradeWebViewPackageInfo = "";
        }
        upgradeWebViewPackageTextView.setText(upgradeWebViewPackageInfo);
    }


    private void updateUpgradeWebViewStatus() {
        if (WebViewUpgrade.isProcessing()) {
            upgradeStatusTextView.setText("upgrading...");
        } else if (WebViewUpgrade.isFailed()) {
            upgradeStatusTextView.setText("fail");
        } else if (WebViewUpgrade.isCompleted()) {
            upgradeStatusTextView.setText("complete");
        } else {
            upgradeStatusTextView.setText("");
        }
        int process = (int) (WebViewUpgrade.getUpgradeProcess() * 100);
        progressBar.setProgress(process);
        upgradeProgressTextView.setText(process + "%");
        Throwable throwable = WebViewUpgrade.getUpgradeError();
        if (throwable == null) {
            upgradeErrorTextView.setText("");
        } else {
            upgradeErrorTextView.setText("message:" + throwable.getMessage() + "\nstackTrace:" + Log.getStackTraceString(throwable));
        }
    }

    static class UpgradeInfo {

        public UpgradeInfo(String packageName, String versionName, String url, String extraInfo) {
            this.title = packageName + "\n" + versionName;
            this.extraInfo = !TextUtils.isEmpty(extraInfo) ? extraInfo : "";
            if (!extraInfo.isEmpty()) {
                this.title = this.title + "\n" + extraInfo;
            }
            this.url = url;
            this.packageName = packageName;
            this.versionName = versionName;
        }

        public UpgradeInfo(String packageName, String versionName, String url) {

            this(packageName, versionName, url, "");
        }

        String title;
        String url;
        String packageName;

        String versionName;
        String extraInfo;
    }


}