package com.norman.webviewup.lib.util;

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
                    byte[] buffer = new byte[8192];
                    InputStream inputStream = null;
                    OutputStream outputStream = null;
                    BufferedInputStream bufferedInput = null;
                    BufferedOutputStream bufferedOutput = null;
                    try {
                        inputStream = zipFile.getInputStream(entry);
                        String[] split = entry.getName().split("/");
                        File targetFile = new File(soDir, abi + "/" + split[split.length - 1]);
                        if (!targetFile.exists()) {
                            File fileParentDir = targetFile.getParentFile();
                            if (fileParentDir != null && !fileParentDir.exists()) {
                                fileParentDir.mkdirs();
                            }
                            targetFile.createNewFile();
                        }
                        outputStream = new FileOutputStream(targetFile);
                        bufferedInput = new BufferedInputStream(inputStream);
                        bufferedOutput = new BufferedOutputStream(outputStream);
                        int count;
                        while ((count = bufferedInput.read(buffer)) > 0) {
                            bufferedOutput.write(buffer, 0, count);
                        }
                        bufferedOutput.flush();
                    } finally {
                        if (bufferedOutput != null) {
                            bufferedOutput.close();
                        }
                        if (outputStream != null) {
                            outputStream.close();
                        }
                        if (bufferedInput != null) {
                            bufferedInput.close();
                        }
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    }

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
}
