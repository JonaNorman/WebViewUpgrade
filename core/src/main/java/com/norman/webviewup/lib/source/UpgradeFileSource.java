package com.norman.webviewup.lib.source;


import android.content.Context;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class UpgradeFileSource extends UpgradePathSource {
    private final String path;

    public UpgradeFileSource(Context context, File file) {
        super(context);
        this.path = file != null ? file.getPath() : null;
    }

    @Override
    public synchronized String getPath() {
        return path;
    }

    @Override
    protected void onPrepare(Object params) {
        error(new IOException("file not exist, path is " + path));
    }
}
