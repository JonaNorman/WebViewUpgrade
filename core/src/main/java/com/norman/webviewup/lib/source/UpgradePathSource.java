package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.FileUtils;

public abstract class UpgradePathSource extends UpgradeSource {
    private static final String PREFERENCE_NAME = "UPGRADE_PATH_SOURCE";


    private final SharedPreferences sharedPreferences;

    public UpgradePathSource(@NonNull Context context) {
        super(context);
        this.sharedPreferences = context
                .getSharedPreferences(PREFERENCE_NAME,
                        Context.MODE_PRIVATE);
    }

    public abstract String getPath();

    @Override
    protected void onSuccess() {
        super.onSuccess();
        sharedPreferences.edit().putBoolean(getPath(), true).commit();
    }

    @Override
    public synchronized boolean isSuccess() {
        if (super.isSuccess()) {
            return true;
        }
        if (sharedPreferences.getBoolean(getPath(), false)) {
            if (FileUtils.isNotEmpty(getPath())) {
                success();
                return true;
            }
            sharedPreferences.edit().putBoolean(getPath(), false).commit();
        }
        return false;
    }
}
