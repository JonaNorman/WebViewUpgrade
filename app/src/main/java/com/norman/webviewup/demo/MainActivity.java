package com.norman.webviewup.demo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
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

import cn.hle.skipselfstartmanager.util.MobileInfoUtils;


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
                        "com.google.android.webview",
                        "122.0.6261.64",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.google.android.webview_122.0.6261.64_arm64-v8a.zip",
                        "网络"),
                new UpgradeInfo(
                        "com.huawei.webview",
                        "14.0.0.331",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.huawei.webview_14.0.0.331_arm64-v8a_armeabi-v7a.zip",
                        "网络"),
                //https://mirrors.aliyun.com/android.googlesource.com/external/chromium-webview/prebuilt/arm64/
                new UpgradeInfo(
                        "com.android.webview",
                        "109.0.5414.123",
                        "",
                        "安装包")
        ));

        UPGRADE_PACKAGE_MAP.put("x86", Arrays.asList(
                new UpgradeInfo(
                        "com.google.android.webview",
                        "122.0.6261.64",
                        "https://mirror.ghproxy.com/https://raw.githubusercontent.com/JonaNorman/ShareFile/main/com.google.android.webview_122.0.6261.64_x86.zip",
                        "网络"),
                new UpgradeInfo("com.google.android.webview",
                        "131.0.6778.105",
                        "com.google.android.webview_131.0.6778.105-677810506_minAPI26_maxAPI28(x86)(nodpi)_apkmirror.com.apk",
                        "内置")
        ));
        UPGRADE_PACKAGE_MAP.put("x86_64", Arrays.asList(
                new UpgradeInfo(
                        "com.google.android.webview",
                        "131.0.6778.135",
                        "https://www.ghproxy.cn/https://github.com/VoryWork/AndroidWebviewNew/releases/download/131.0.6778.135/x64.apk",
                        "网络"),
                // https://github.com/bromite/bromite/releases/download/108.0.5359.156/x64_SystemWebView.apk
                new UpgradeInfo(
                        "org.bromite.webview",
                        "108.0.5359.156",
                        "",
                        "安装包")
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

        assert upgradeInfoList != null;
        String[] items = new String[upgradeInfoList.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = upgradeInfoList.get(i).title;
        }

        Context context = this;

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
//                    if (systemWebViewPackageName != null &&systemWebViewPackageName.equals(upgradeInfo.packageName)
//                            && VersionUtils.compareVersion( WebViewUpgrade.getSystemWebViewPackageVersion(),upgradeInfo.versionName) >= 0) {
//                        Toast.makeText(getApplicationContext(), "system webView is larger than the one to be upgraded, so there is no need to upgrade", Toast.LENGTH_LONG).show();
//                        return;
//                    }
                    selectUpgradeInfo = upgradeInfo;
                    UpgradeSource upgradeSource = getUpgradeSource(upgradeInfo);
                    if (upgradeSource == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT > 34 && upgradeInfo.extraInfo.equals("安装包")) {
                        String serviceName =  "org.chromium.content.app.SandboxedProcessService0";
                        ServiceConnection mConnection = new ServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName className, IBinder service) {
                                Log.e("MainActivity", serviceName + "服务连接成功");
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName arg0) {
                                Log.e("MainActivity", serviceName + "服务意外断开");
                            }
                        };

                        try {
                            Intent intent = new Intent();
                            intent.setClassName(upgradeInfo.packageName, serviceName);
                            boolean isServiceBound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

                            if (isServiceBound) {
                                // 理论上没有这个情况，因为不可能成功的
                                Log.e("MainActivity", serviceName + "服务已启动并且绑定成功");
                            }
                            else {
                                Log.e("MainActivity", serviceName + "是服务未启动或不存在");
                                android.app.AlertDialog.Builder dlg = new android.app.AlertDialog.Builder(context);
                                dlg.setMessage("请授予Webview(" + upgradeInfo.packageName + ")自启动权限后重新进入APP，否则本App将无法正常使用Webview组件！");
                                dlg.setTitle("Alert");
                                dlg.setCancelable(false);
                                dlg.setPositiveButton("立即设置",
                                        (dialog1, which1) -> navigateToAppSettingsAndExit());
                                dlg.setNegativeButton("暂时不设置",
                                        (dialog3, which2) -> dialog3.dismiss());
                                dlg.setOnKeyListener((dialog2, keyCode, event) -> {
                                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                                        return false;
                                    }
                                    else {
                                        navigateToAppSettingsAndExit();
                                        return true;
                                    }
                                });
                                dlg.show();
                            }
                        } catch (java.lang.SecurityException e) {
                            // 有SecurityException就证明webview在后台启动了
                            Log.e("MainActivity", serviceName + "服务已启动");
                        }
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

    private void navigateToAppSettingsAndExit() {
        MobileInfoUtils.jumpStartInterface(this);
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
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