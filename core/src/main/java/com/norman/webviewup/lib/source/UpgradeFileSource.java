package com.norman.webviewup.lib.source;


import android.content.Context;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class UpgradeFileSource extends UpgradePathSource {

    public UpgradeFileSource(Context context, File file) {
        super(context, file.getPath());
    }


    @Override
    protected void onPrepare(Object params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!FileUtils.existFile(getApkPath())) {
                    error(new IOException("file not exist, path is " + getApkPath()));
                } else {
                    success();
                }
            }
        }).start();

    }
}
