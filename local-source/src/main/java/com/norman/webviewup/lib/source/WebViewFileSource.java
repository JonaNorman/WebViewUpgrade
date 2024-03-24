package com.norman.webviewup.lib.source;


import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class WebViewFileSource extends WebViewPathSource {
    private final String path;

    public WebViewFileSource(String path) {
        this.path = path;
    }

    public WebViewFileSource(File file) {
        this.path = file != null ? file.getPath() : null;
    }

    @Override
    public synchronized String getPath() {
        return path;
    }

    @Override
    protected void onPrepare() {
        if (FileUtils.isNotEmptyFile(path)) {
            success();
        } else {
            error(new IOException("file not exist, path is " + path));
        }
    }
}
