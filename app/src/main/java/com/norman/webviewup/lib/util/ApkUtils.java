package com.norman.webviewup.lib.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkUtils {


    public static void extractNativeLibrary(String apkPath, String soDir) {
        ZipFile zipFile = null;
        try {
            if (TextUtils.isEmpty(apkPath)) {
                throw new NullPointerException("apkPath is empty");
            }
            if (TextUtils.isEmpty(soDir)) {
                throw new NullPointerException("apkPath is empty");
            }
            zipFile = new ZipFile(new File(apkPath));
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            Map<String, List<ZipEntry>> soLibEntryMap = new HashMap<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.contains("../") || entry.isDirectory()) {
                    continue;
                }
                if (!entryName.startsWith("lib/") && !entryName.endsWith(".so")) {
                    continue;
                }
                String[] split = entry.getName().split("/");
                if (split.length >= 3) {
                    String abi = split[1];
                    List<ZipEntry> list = soLibEntryMap.get(abi);
                    if (list == null) {
                        list = new ArrayList<>();
                        soLibEntryMap.put(abi, list);
                    }
                    list.add(entry);
                }
            }
            String[] supportedAbis = Build.SUPPORTED_ABIS;
            for (String abi : supportedAbis) {
                List<ZipEntry> entryList = soLibEntryMap.get(abi);
                if (entryList == null) continue;
                for (ZipEntry entry : entryList) {
                    String[] split = entry.getName().split("/");
                    File targetFile = new File(soDir, abi + "/" + split[split.length - 1]);
                    FileUtils.copyFile(zipFile.getInputStream(entry), targetFile, true);
                }
            }

        } catch (IOException ioException) {
            FileUtils.cleanDirectory(soDir);
            throw new RuntimeException(ioException);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ignore) {

                }
            }
        }
    }

    public static PackageInfo extractApk(Context context,
                                  String filePath,
                                  String apkPath) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageArchiveInfo(filePath, 0);
        } catch (Throwable ignore) {

        }
        if (packageInfo == null) {
            try (ZipFile zipFile = new ZipFile(filePath)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.endsWith(".apk")) {
                        FileUtils.copyFile(zipFile.getInputStream(entry), new File(apkPath), true);
                        packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
                        break;
                    }
                }

            } catch (IOException throwable) {
                throw new RuntimeException(throwable);
            }
        }else {
            FileUtils.copyFile(filePath, apkPath);
        }
        if (packageInfo == null) {
            throw new RuntimeException("path:" + filePath + " not exist apk");
        } else {
            return packageInfo;
        }
    }
}
