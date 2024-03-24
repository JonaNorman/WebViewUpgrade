package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.text.TextUtils;

import com.norman.webviewup.lib.UpgradeDirectory;
import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.Md5Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class WebViewAssetSource extends WebViewPathSource {

    private final String assetName;

    private final Context context;

    private final String path;

    public WebViewAssetSource(Context context, String assetName, String path) {
        this.assetName = assetName;
        this.context = context == null ? null : context.getApplicationContext();
        if (TextUtils.isEmpty(path) && context != null && !TextUtils.isEmpty(assetName)) {
            File apkFile = UpgradeDirectory.getApkFile(context, Md5Utils.getMd5(assetName));
            this.path = apkFile.getPath();
        } else {
            this.path = path;
        }
    }

    public WebViewAssetSource(Context context, String assetName) {
        this(context, assetName, null);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    protected void onPrepare() {
        if (FileUtils.isNotEmptyFile(path)) {
            success();
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    FileOutputStream outputStream = null;
                    FileInputStream inputStream = null;
                    try {
                        outputStream = new FileOutputStream(path);
                        FileChannel dstChannel = outputStream.getChannel();
                        AssetManager assetManager = context.getAssets();
                        AssetFileDescriptor assetFileDescriptor = assetManager.openFd(assetName);
                        inputStream = assetFileDescriptor.createInputStream();
                        FileChannel fileChannel = inputStream.getChannel();
                        long startOffset = assetFileDescriptor.getStartOffset();
                        long declaredLength = assetFileDescriptor.getDeclaredLength();
                        fileChannel.transferTo(startOffset, declaredLength, dstChannel);

                    } catch (Throwable e) {
                        FileUtils.delete(path);
                        throw new RuntimeException(e);
                    } finally {
                        if (outputStream != null) {
                            try {
                                outputStream.close();
                            } catch (IOException ignore) {

                            }
                        }
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException ignore) {

                            }
                        }
                    }

                }
            }).start();
        }
    }
}
