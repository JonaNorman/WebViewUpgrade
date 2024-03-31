package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.ApksUtils;
import com.norman.webviewup.lib.util.FileUtils;

public abstract class UpgradePathSource extends UpgradeSource {
    private static final String PREFERENCE_NAME = "UPGRADE_PATH_SOURCE";
    private final SharedPreferences sharedPreferences;

    private final String libsPath;

    private final String path;


    public UpgradePathSource(@NonNull Context context, String path) {
        super(context);
        this.path = path;
        this.libsPath = path + "-libs";
        this.sharedPreferences = context
                .getSharedPreferences(PREFERENCE_NAME,
                        Context.MODE_PRIVATE);
    }

    public synchronized void delete(){
        this.sharedPreferences.edit().remove(this.path).commit();
        FileUtils.delete(path);
        FileUtils.delete(this.libsPath);
    }

    public String getApkPath() {
        return path;
    }

    public String getLibsPath() {
        return libsPath;
    }

    @Override
    protected void onSuccess() {
        super.onSuccess();
        ApksUtils.extractNativeLibrary(path, libsPath);
        sharedPreferences.edit().putBoolean(getApkPath(), true).commit();
    }

    @Override
    public synchronized boolean isSuccess() {
        if (super.isSuccess()) {
            return true;
        }
        if (sharedPreferences.getBoolean(getApkPath(), false)) {
            if (FileUtils.isNotEmpty(getApkPath())) {
                success();
                return true;
            }
            sharedPreferences.edit().putBoolean(getApkPath(), false).commit();
        }
        return false;
    }
}
