package com.norman.webviewup.demo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.norman.webviewup.demo.BuildConfig;
import com.norman.webviewup.demo.catalog.DemoUpgradeChoice;
import com.norman.webviewup.demo.catalog.WebViewPackageCatalog;
import com.norman.webviewup.lib.UpgradeCallback;
import com.norman.webviewup.lib.WebViewUpgrade;
import com.norman.webviewup.lib.source.UpgradeSource;
import com.norman.webviewup.lib.util.RestartUtil;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements UpgradeCallback {

    private static final String TAG = "MainActivity";

    ProgressBar progressBar;
    TextView systemWebViewPackageTextView;
    TextView chromePackageTextView;
    TextView upgradeWebViewPackageTextView;
    TextView kernelPendingTextView;

    TextView upgradeStatusTextView;
    TextView upgradeErrorTextView;

    TextView upgradeProgressTextView;

    Button upgradeButton;
    Button restoreButton;

    @Nullable
    DemoUpgradeChoice selectUpgradeChoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WebViewUpgrade.addUpgradeCallback(this);
        progressBar = findViewById(R.id.upgradeProgressBar);
        systemWebViewPackageTextView = findViewById(R.id.systemWebViewPackageTextView);
        chromePackageTextView = findViewById(R.id.chromePackageTextView);
        upgradeWebViewPackageTextView = findViewById(R.id.upgradeWebViewPackageTextView);
        kernelPendingTextView = findViewById(R.id.kernelPendingTextView);
        upgradeStatusTextView = findViewById(R.id.upgradeStatusTextView);
        upgradeErrorTextView = findViewById(R.id.upgradeErrorTextView);
        upgradeProgressTextView = findViewById(R.id.upgradeProgressTextView);
        upgradeButton = findViewById(R.id.upgradeButton);
        restoreButton = findViewById(R.id.restoreButton);

        upgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChooseKernelClicked();
            }
        });

        findViewById(R.id.webViewButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WebViewActivity.class));
            }
        });

        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRestoreConfirmDialog();
            }
        });

        refreshAllUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WebViewUpgrade.removeUpgradeCallback(this);
    }

    private void refreshAllUi() {
        updateSystemWebViewPackageInfo();
        updateChromePackageInfo();
        updateUpgradeWebViewPackageInfo();
        updateKernelPendingLine();
        updateUpgradeWebViewStatus();
        updateActionButtons();
    }

    private void onChooseKernelClicked() {
        if (WebViewUpgrade.isProcessing()) {
            Toast.makeText(getApplicationContext(), R.string.wv_upgrade_in_progress, Toast.LENGTH_LONG).show();
            return;
        }
        if (WebViewUpgrade.isCompleted()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.wv_change_requires_restart_title)
                    .setMessage(R.string.wv_change_requires_restart_message)
                    .setPositiveButton(R.string.wv_action_continue, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openKernelPicker(true);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        openKernelPicker(false);
    }

    private void showRestoreConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.wv_restore_confirm_title)
                .setMessage(R.string.wv_restore_confirm_message)
                .setPositiveButton(R.string.wv_action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PreferredWebViewStore.clear(MainActivity.this);
                        RestartUtil.restart(getApplication(),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        DemoWebViewHolder.destroyHeldWebView();
                                    }
                                });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * @param restartAfterSelection if true, persist choice and hard-restart (used when upgrade already completed this process).
     */
    private void openKernelPicker(final boolean restartAfterSelection) {
        List<DemoUpgradeChoice> choices;
        try {
            choices = WebViewPackageCatalog.buildCatalogChoices(
                    this,
                    BuildConfig.CATALOG_PREFER_GHPROXY);
            choices = WebViewPackageCatalog.appendInstalledChrome(
                    this,
                    choices,
                    getPackageManager());
        } catch (Exception e) {
            Log.e(TAG, "catalog load failed", e);
            Toast.makeText(this, getString(R.string.wv_catalog_load_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            return;
        }
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.wv_no_matching_packages, Toast.LENGTH_LONG).show();
            return;
        }

        final List<DemoUpgradeChoice> list = new ArrayList<>(choices);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.wv_dialog_title);
        ArrayAdapter<DemoUpgradeChoice> adapter = new ArrayAdapter<DemoUpgradeChoice>(
                this, 0, list) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(getContext()).inflate(
                            R.layout.dialog_item_webview_choice, parent, false);
                }
                DemoUpgradeChoice c = list.get(position);
                TextView lineVendorArch = row.findViewById(R.id.wv_choice_line_vendor_arch);
                TextView lineStatusVer = row.findViewById(R.id.wv_choice_line_status_version);
                lineVendorArch.setText(c.lineVendorArch);
                lineStatusVer.setText(buildStatusVersionLine(getContext(), c));
                return row;
            }
        };
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (WebViewUpgrade.isProcessing()) {
                    Toast.makeText(getApplicationContext(), R.string.wv_upgrade_in_progress, Toast.LENGTH_LONG).show();
                    return;
                }
                DemoUpgradeChoice choice = list.get(which);
                UpgradeSource upgradeSource = choice.toUpgradeSource(MainActivity.this);
                if (upgradeSource == null) {
                    Toast.makeText(getApplicationContext(), R.string.wv_invalid_upgrade_source, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (restartAfterSelection) {
                    selectUpgradeChoice = choice;
                    PreferredWebViewStore.save(MainActivity.this, choice);
                    RestartUtil.restart(getApplication(),
                            new Runnable() {
                                @Override
                                public void run() {
                                    DemoWebViewHolder.destroyHeldWebView();
                                }
                            });
                    return;
                }
                if (WebViewUpgrade.isCompleted()) {
                    Toast.makeText(getApplicationContext(), R.string.wv_upgrade_already_done, Toast.LENGTH_LONG).show();
                    return;
                }
                selectUpgradeChoice = choice;
                WebViewUpgrade.upgrade(upgradeSource);
                updateUpgradeWebViewPackageInfo();
                updateKernelPendingLine();
                updateUpgradeWebViewStatus();
                updateActionButtons();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        ListView lv = dialog.getListView();
        if (lv != null) {
            lv.setDivider(new ColorDrawable(0x00000000));
            lv.setDividerHeight(0);
        }
    }

    @Override
    public void onUpgradeProcess(float percent) {
        updateUpgradeWebViewStatus();
        updateKernelPendingLine();
        updateActionButtons();
    }

    @Override
    public void onUpgradeComplete() {
        if (selectUpgradeChoice != null) {
            PreferredWebViewStore.save(this, selectUpgradeChoice);
        }
        updateUpgradeWebViewStatus();
        updateUpgradeWebViewPackageInfo();
        updateKernelPendingLine();
        updateActionButtons();
        Toast.makeText(getApplicationContext(), R.string.wv_upgrade_success, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUpgradeError(Throwable throwable) {
        Toast.makeText(getApplicationContext(), R.string.wv_upgrade_fail, Toast.LENGTH_SHORT).show();
        Log.e(TAG, "message:" + throwable.getMessage() + "\nstackTrace:" + Log.getStackTraceString(throwable));
        updateUpgradeWebViewStatus();
        updateKernelPendingLine();
        updateActionButtons();
    }

    private void updateKernelPendingLine() {
        boolean show = PreferredWebViewStore.hasChoice(this)
                && !WebViewUpgrade.isCompleted()
                && !WebViewUpgrade.isFailed();
        kernelPendingTextView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateActionButtons() {
        boolean processing = WebViewUpgrade.isProcessing();
        upgradeButton.setEnabled(!processing);
        boolean canRestore = PreferredWebViewStore.hasChoice(this) || WebViewUpgrade.isCompleted();
        restoreButton.setEnabled(canRestore);
        restoreButton.setAlpha(canRestore ? 1f : 0.45f);
    }

    /**
     * Line 2 only: colored status · muted version (long version wraps here, not on vendor/ABI line).
     */
    @NonNull
    private static CharSequence buildStatusVersionLine(
            @NonNull android.content.Context ctx,
            @NonNull DemoUpgradeChoice c) {
        int muted = ContextCompat.getColor(ctx, R.color.wv_text_secondary);
        int tone = ContextCompat.getColor(ctx, c.statusTone.colorRes);
        String sep = ctx.getString(R.string.wv_choice_status_version_sep);
        SpannableStringBuilder ssb = new SpannableStringBuilder();

        int sStatus = ssb.length();
        ssb.append(c.statusLabel);
        ssb.setSpan(new ForegroundColorSpan(tone), sStatus, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), sStatus, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int sSep = ssb.length();
        ssb.append(sep);
        ssb.setSpan(new ForegroundColorSpan(muted), sSep, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int sVer = ssb.length();
        ssb.append(c.lineVersion);
        ssb.setSpan(new ForegroundColorSpan(muted), sVer, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }

    private void updateSystemWebViewPackageInfo() {
        String systemWebViewPackageName = WebViewUpgrade.getSystemWebViewPackageName();
        String systemWebViewPackageVersion = WebViewUpgrade.getSystemWebViewPackageVersion();

        String unknown = getString(R.string.wv_label_unknown);
        String systemWebViewPackageInfo = unknown;
        if (!TextUtils.isEmpty(systemWebViewPackageName)
                || !TextUtils.isEmpty(systemWebViewPackageVersion)) {
            systemWebViewPackageInfo = (!TextUtils.isEmpty(systemWebViewPackageName) ? systemWebViewPackageName : unknown)
                    + ":" + (!TextUtils.isEmpty(systemWebViewPackageVersion) ? systemWebViewPackageVersion : unknown);
        }
        systemWebViewPackageTextView.setText(systemWebViewPackageInfo);
    }

    private void updateChromePackageInfo() {
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageInfo(
                    WebViewPackageCatalog.CHROME_PACKAGE, 0);
            String ver = info.versionName != null ? info.versionName : String.valueOf(info.versionCode);
            chromePackageTextView.setText(WebViewPackageCatalog.CHROME_PACKAGE + ":" + ver);
        } catch (PackageManager.NameNotFoundException e) {
            chromePackageTextView.setText(
                    WebViewPackageCatalog.CHROME_PACKAGE + ": " + getString(R.string.wv_chrome_not_installed));
        }
    }

    private void updateUpgradeWebViewPackageInfo() {
        String runtimeName = WebViewUpgrade.getUpgradeWebViewPackageName();
        String runtimeVer = WebViewUpgrade.getUpgradeWebViewVersion();
        String unknown = getString(R.string.wv_label_unknown);

        if (!TextUtils.isEmpty(runtimeName) || !TextUtils.isEmpty(runtimeVer)) {
            String info = (!TextUtils.isEmpty(runtimeName) ? runtimeName : unknown)
                    + ":" + (!TextUtils.isEmpty(runtimeVer) ? runtimeVer : unknown);
            upgradeWebViewPackageTextView.setText(info);
            return;
        }

        String storedPkg = PreferredWebViewStore.getDisplayPackageName(this);
        String storedVer = PreferredWebViewStore.getDisplayVersion(this);
        if (!TextUtils.isEmpty(storedPkg) || !TextUtils.isEmpty(storedVer)) {
            String info = (!TextUtils.isEmpty(storedPkg) ? storedPkg : unknown)
                    + ":" + (!TextUtils.isEmpty(storedVer) ? storedVer : unknown);
            upgradeWebViewPackageTextView.setText(info);
            return;
        }

        if (selectUpgradeChoice != null) {
            String pn = selectUpgradeChoice.packageName;
            String vl = selectUpgradeChoice.versionLabel;
            String info = (!TextUtils.isEmpty(pn) ? pn : unknown)
                    + ":" + (!TextUtils.isEmpty(vl) ? vl : unknown);
            upgradeWebViewPackageTextView.setText(info);
            return;
        }

        upgradeWebViewPackageTextView.setText(R.string.wv_no_custom_kernel);
    }

    private void updateUpgradeWebViewStatus() {
        if (WebViewUpgrade.isProcessing()) {
            upgradeStatusTextView.setText(R.string.wv_status_upgrading);
        } else if (WebViewUpgrade.isFailed()) {
            upgradeStatusTextView.setText(R.string.wv_status_fail);
        } else if (WebViewUpgrade.isCompleted()) {
            upgradeStatusTextView.setText(R.string.wv_status_complete);
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
            upgradeErrorTextView.setText(
                    getString(R.string.wv_error_message_prefix)
                            + (throwable.getMessage() != null ? throwable.getMessage() : "")
                            + "\n"
                            + Log.getStackTraceString(throwable));
        }
    }
}
