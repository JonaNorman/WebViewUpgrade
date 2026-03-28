package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.ApksUtils;
import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;

public abstract class UpgradePathSource extends UpgradeSource {
    private static final String PREFERENCE_NAME = "UPGRADE_PATH_SOURCE";
    private final SharedPreferences sharedPreferences;

    private final String internalApkPath;
    private final String internalLibsPath;
    private final String archiveKey;

    /**
     * 内部沙盒模式 (新架构)
     * 传入一个版本标识符，框架自动分配并管理私有目录
     */
    public UpgradePathSource(@NonNull Context context, String identifier) {
        super(context);
        this.archiveKey = identifier;

        File webviewUpDir = context.getDir("package_webview", Context.MODE_PRIVATE);
        
        String md5DirName = FileUtils.md5(identifier);
        File workSpaceDir = new File(webviewUpDir, md5DirName);
        if (!workSpaceDir.exists()) {
            workSpaceDir.mkdirs();
        }

        this.internalApkPath = new File(workSpaceDir, "base.apk").getAbsolutePath();
        this.internalLibsPath = new File(workSpaceDir, "libs").getAbsolutePath();
        
        this.sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 外部指定模式 (兼容老 API)
     * 用户强制指定了存放路径 (externalPath)
     * @deprecated 请使用 {@link #UpgradePathSource(Context, String)} 由框架自动管理存储路径
     */
    @Deprecated
    public UpgradePathSource(@NonNull Context context, String externalPath, boolean isExternal) {
        super(context);
        this.archiveKey = externalPath;
        this.internalApkPath = externalPath;
        this.internalLibsPath = externalPath + "-libs";
        
        this.sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void delete(){
        this.sharedPreferences.edit().remove(this.archiveKey).commit();
        FileUtils.delete(this.internalApkPath);
        FileUtils.delete(this.internalLibsPath);
    }

    public String getApkPath() {
        return internalApkPath;
    }

    public String getLibsPath() {
        return internalLibsPath;
    }

    @Override
    protected void onSuccess() {
        super.onSuccess();
        ApksUtils.extractNativeLibrary(getApkPath(), getLibsPath());
        FileUtils.makeFileWorldReadable(getContext(), new File(getApkPath()));
        sharedPreferences.edit().putBoolean(this.archiveKey, true).commit();
    }

    @Override
    public synchronized boolean isSuccess() {
        if (super.isSuccess()) {
            return true;
        }
        if (sharedPreferences.getBoolean(this.archiveKey, false)) {
            if (FileUtils.isNotEmpty(getApkPath())) {
                success();
                return true;
            }
            sharedPreferences.edit().putBoolean(this.archiveKey, false).commit();
        }
        return false;
    }
}
