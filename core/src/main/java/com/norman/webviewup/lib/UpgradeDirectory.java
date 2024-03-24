package com.norman.webviewup.lib;

import android.content.Context;

import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.Md5Utils;

import java.io.File;

public class UpgradeDirectory {

    private static final String UPGRADE_DIRECTORY_NAME = "WebViewUpgrade";

    private static final String APK_DIRECTORY_NAME = "apk";

    public static File getRoot(Context context) {
        return new File(context.getFilesDir(),
                UPGRADE_DIRECTORY_NAME);
    }


    public static File createDirectory(Context context, String child) {
        File childDir = new File(getRoot(context), child);
        FileUtils.makeDirectory(childDir);
        return childDir;
    }

    public static File getApkDirectory(Context context) {
        return createDirectory(context, APK_DIRECTORY_NAME);
    }

    public static File getApkFile(Context context, String name) {
        File apkFile = new File(getApkDirectory(context), name);
        FileUtils.createFile(apkFile);
        return apkFile;
    }

}
