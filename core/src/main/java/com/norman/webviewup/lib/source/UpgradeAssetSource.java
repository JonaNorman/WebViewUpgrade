package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class UpgradeAssetSource extends UpgradePathSource {

    private final String assetName;
    private final String path;


    private final boolean mainThread;

    public UpgradeAssetSource(Context context, @NonNull String assetName, @NonNull File file) {
        this(context, assetName, file, false);
    }

    public UpgradeAssetSource(Context context, @NonNull String assetName, @NonNull File file, boolean mainThread) {
        super(context);
        this.assetName = assetName;
        this.path = file.getPath();
        this.mainThread = mainThread;
    }

    @Override
    public String getPath() {
        return path;
    }

    private final Runnable copyAssetRunnable = new Runnable() {
        @Override
        public void run() {
            FileOutputStream outputStream = null;
            FileInputStream inputStream = null;
            try {
                FileUtils.createFile(path);
                outputStream = new FileOutputStream(path);
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
                FileUtils.delete(path);
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
        if (mainThread) {
            copyAssetRunnable.run();
            return;
        }
        new Thread(copyAssetRunnable).start();
    }
}
