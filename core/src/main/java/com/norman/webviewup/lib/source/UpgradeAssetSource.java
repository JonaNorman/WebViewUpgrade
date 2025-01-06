package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.ApksUtils;
import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class UpgradeAssetSource extends UpgradePathSource {

    private final String assetName;


    public UpgradeAssetSource(Context context, @NonNull String assetName, @NonNull File file) {
        super(context,file.getPath());
        this.assetName = assetName;
    }

    private final Runnable copyAssetRunnable = new Runnable() {
        @Override
        public void run() {
            FileOutputStream outputStream = null;
            FileInputStream inputStream = null;
            try {
                FileUtils.createFile(getApkPath());
                outputStream = new FileOutputStream(getApkPath());
                FileChannel dstChannel = outputStream.getChannel();
                AssetManager assetManager = getContext().getAssets();
                AssetFileDescriptor assetFileDescriptor = assetManager.openFd(assetName);
                inputStream = assetFileDescriptor.createInputStream();
                FileChannel fileChannel = inputStream.getChannel();
                long startOffset = assetFileDescriptor.getStartOffset();
                long declaredLength = assetFileDescriptor.getDeclaredLength();
                int size = 100;
                long partSize = (long) Math.ceil(declaredLength * 1.0 / size);
                long position = startOffset;
                for (int i = 0; i < size; i++) {
                    long count = i != size - 1 ? partSize : declaredLength - i * partSize;
                    fileChannel.transferTo(position, count, dstChannel);
                    process(i * 1.0f / size);
                    position = position + count;
                }
                success();
            } catch (Throwable e) {
                FileUtils.delete(getApkPath());
                error(e);
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
    };

    @Override
    protected void onPrepare(Object params) {
        new Thread(copyAssetRunnable).start();
    }
}
