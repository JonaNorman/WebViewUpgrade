package com.norman.webviewup.lib.util;

import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApksUtils {


    public static void extractNativeLibrary(String apkPath, String libsDir) {
        ZipFile zipFile = null;
        try {
            if (TextUtils.isEmpty(apkPath)) {
                throw new NullPointerException("apkPath is empty");
            }
            if (TextUtils.isEmpty(libsDir)) {
                throw new NullPointerException("apkPath is empty");
            }
            zipFile = new ZipFile(new File(apkPath));
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            List<ZipEntry> libsEntryList = new ArrayList<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.contains("../") || entry.isDirectory()) {
                    continue;
                }
                if (!entryName.startsWith("lib/") && !entryName.endsWith(".so")) {
                    continue;
                }
                libsEntryList.add(entry);
            }
            for (ZipEntry zipEntry : libsEntryList) {
                String[] split = zipEntry.getName().split("/");
                File targetFile = new File(libsDir, split[1] + "/" + split[split.length - 1]);
                FileUtils.copyFile(zipFile.getInputStream(zipEntry), targetFile, true);
            }
        } catch (IOException ioException) {
            FileUtils.cleanDirectory(libsDir);
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
}
