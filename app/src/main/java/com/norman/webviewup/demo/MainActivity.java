package com.norman.webviewup.demo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AlertDialog;

import com.norman.webviewup.lib.UpgradeCallback;
import com.norman.webviewup.lib.UpgradeOptions;
import com.norman.webviewup.lib.WebViewUpgrade;

import java.util.Arrays;
import java.util.List;


public class MainActivity extends Activity implements UpgradeCallback {

    private static final List<UpgradeInfo> UPGRADE_PACKAGE_LIST = Arrays.asList(
            new UpgradeInfo(
                    "com.google.android.webview",
                    "122.0.6261.64",
                    "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.google.android.webview_122.0.6261.64_armeabi-v7a.zip"),
            new UpgradeInfo(
                    "com.android.webview",
                    "113.0.5672.136",
                    "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.android.webview_113.0.5672.13_armeabi-v7a.zip"),
            new UpgradeInfo(
                    "com.huawei.webview",
                    "14.0.0.331",
                    "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.huawei.webview_14.0.0.331_arm64-v8a_armeabi-v7a.zip")

    );



    ProgressBar progressBar;
    TextView systemWebViewPackageTextView;
    TextView upgradeWebViewPackageTextView;

    TextView upgradeStatusTextView;
    TextView upgradeErrorTextView;

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
        updateSystemWebViewPackageInfo();
        updateSystemWebViewPackageInfo();
        updateUpgradeWebViewStatus();


        findViewById(R.id.upgradeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (WebViewUpgrade.isProcessing()) {
                    Toast.makeText(getApplicationContext(), "webView is being upgraded, please wait", Toast.LENGTH_LONG).show();
                }  else if (WebViewUpgrade.isCompleted()) {
                    Toast.makeText(getApplicationContext(), "webView is already upgrade success,not support dynamic switch", Toast.LENGTH_LONG).show();
                } else {
                    showChooseWebViewDialog();
                }
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
        updateUpgradeWebViewStatus();
    }

    private void showChooseWebViewDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose WebView");

        String[] items = new String[UPGRADE_PACKAGE_LIST.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = UPGRADE_PACKAGE_LIST.get(i).title;
        }
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                UpgradeInfo upgradeInfo = UPGRADE_PACKAGE_LIST.get(which);
                selectUpgradeInfo = upgradeInfo;
                UpgradeOptions upgradeOptions = new UpgradeOptions
                        .Builder(getApplicationContext(),
                        upgradeInfo.packageName,
                        upgradeInfo.url,
                        upgradeInfo.versionName,
                        new DownloadSinkImpl())
                        .build();
                WebViewUpgrade.upgrade(upgradeOptions);
                updateUpgradeWebViewPackageInfo();
                updateUpgradeWebViewStatus();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
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
        String upgradeWebViewPackageName = selectUpgradeInfo!= null?selectUpgradeInfo.packageName:null;
        String upgradeWebViewPackageVersion =  selectUpgradeInfo!= null?selectUpgradeInfo.versionName:null;

        String upgradeWebViewPackageInfo = "";
        if (!TextUtils.isEmpty(upgradeWebViewPackageName)
                || !TextUtils.isEmpty(upgradeWebViewPackageVersion)) {
            upgradeWebViewPackageInfo = (!TextUtils.isEmpty(upgradeWebViewPackageName) ? upgradeWebViewPackageName : "unknown")
                    + ":" + (!TextUtils.isEmpty(upgradeWebViewPackageVersion) ? upgradeWebViewPackageVersion : "unknown");
        }else {
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
        } else if (WebViewUpgrade.isInited()) {
            upgradeStatusTextView.setText("init");
        } else {
            upgradeStatusTextView.setText("");
        }
        progressBar.setProgress((int) (WebViewUpgrade.getUpgradeProcess() * 100));
        Throwable throwable = WebViewUpgrade.getUpgradeError();
        if (throwable == null) {
            upgradeErrorTextView.setText("");
        } else {
            upgradeErrorTextView.setText("message:" + throwable.getMessage() + "\nstackTrace:" + Log.getStackTraceString(throwable));
        }
    }

    static class UpgradeInfo {

        public UpgradeInfo(String packageName, String versionName, String url) {
            this.title = packageName + "\n" + versionName;
            this.url = url;
            this.packageName = packageName;
            this.versionName = versionName;
        }

        String title;
        String url;
        String packageName;

        String versionName;
    }


}