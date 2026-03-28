package com.norman.webviewup.lib.source;


import android.content.Context;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class UpgradeFileSource extends UpgradePathSource {

    private final File sourceFile;

    public UpgradeFileSource(Context context, File file) {
        // 使用 文件路径 + 最后修改时间 + 大小 作为标识，保障外部文件更新时能够重新拷贝
        super(context, file.getPath() + "_" + file.lastModified() + "_" + file.length());
        this.sourceFile = file;
    }


    @Override
    protected void onPrepare(Object params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!sourceFile.exists()) {
                    error(new IOException("file not exist, path is " + sourceFile.getAbsolutePath()));
                    return;
                }
                
                if (!FileUtils.existFile(getApkPath())) {
                    try {
                        FileUtils.copyFile(sourceFile.getAbsolutePath(), getApkPath());
                    } catch (IOException e) {
                        error(new IOException("Failed to copy file to internal workspace", e));
                        return;
                    }
                }
                success();
            }
        }).start();

    }
}
