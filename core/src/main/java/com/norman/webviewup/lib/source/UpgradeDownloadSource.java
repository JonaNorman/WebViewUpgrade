package com.norman.webviewup.lib.source;

import android.content.Context;
import android.text.TextUtils;

import com.norman.webviewup.lib.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UpgradeDownloadSource extends UpgradePathSource {

    private final String url;
    private String tempPath;
    private boolean isCancelled = false;

    public UpgradeDownloadSource(Context context, String url) {
        super(context, url);
        this.url = url;
    }

    @Override
    protected void onPrepare(Object params) {
        tempPath = getApkPath() + ".tmp";
        isCancelled = false;

        new Thread(() -> {
            try {
                // 1. 下载文件 (支持断点续传)
                downloadFile();
                if (isCancelled) return;

                // 2. 将临时文件处理并移动/解压为最终 APK
                processDownloadedFile();
                if (isCancelled) return;

                // 3. 成功回调
                success();
            } catch (Throwable e) {
                FileUtils.delete(getApkPath());
                error(e);
            }
        }).start();
    }

    private void downloadFile() throws IOException {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("Download URL is empty");
        }

        File tempFile = new File(tempPath);
        long downloadedSize = tempFile.exists() ? tempFile.length() : 0;

        HttpURLConnection connection = null;
        RandomAccessFile randomAccessFile = null;
        InputStream inputStream = null;

        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            // 设置断点续传的请求头
            if (downloadedSize > 0) {
                connection.setRequestProperty("Range", "bytes=" + downloadedSize + "-");
            }

            connection.connect();
            int responseCode = connection.getResponseCode();
            long totalSize;

            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // 206 断点续传成功
                totalSize = downloadedSize + connection.getContentLength();
                randomAccessFile = new RandomAccessFile(tempFile, "rw");
                randomAccessFile.seek(downloadedSize);
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                // 200 不支持断点续传或文件发生改变，重新下载
                downloadedSize = 0;
                totalSize = connection.getContentLength();
                FileUtils.delete(tempPath);
                FileUtils.createFile(tempPath);
                randomAccessFile = new RandomAccessFile(tempFile, "rw");
            } else {
                throw new IOException("Server returned HTTP " + responseCode);
            }

            inputStream = connection.getInputStream();
            byte[] buffer = new byte[8192];
            int readCount;

            while ((readCount = inputStream.read(buffer)) != -1) {
                if (isCancelled) {
                    return;
                }
                randomAccessFile.write(buffer, 0, readCount);
                downloadedSize += readCount;

                if (totalSize > 0) {
                    // 下载阶段占据总进度的 95%
                    float percent = 0.95f * downloadedSize / totalSize;
                    process(percent);
                }
            }
        } finally {
            if (randomAccessFile != null) {
                try { randomAccessFile.close(); } catch (IOException ignore) {}
            }
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignore) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void processDownloadedFile() throws IOException {
        BufferedInputStream bufferedInput = null;
        BufferedOutputStream bufferedOutput = null;
        ZipFile zipFile = null;
        
        try {
            if (isValidApk(tempPath)) {
                bufferedInput = new BufferedInputStream(new FileInputStream(tempPath));
            } else {
                zipFile = new ZipFile(tempPath);
                bufferedInput = findStreamInZip(zipFile);
                if (bufferedInput == null) {
                    throw new IOException("Cannot find valid APK in the downloaded zip file.");
                }
            }

            FileUtils.createFile(getApkPath());
            bufferedOutput = new BufferedOutputStream(new FileOutputStream(getApkPath()));
            copyBufferStream(bufferedInput, bufferedOutput);
            
            // 提取完毕后删除临时文件
            FileUtils.delete(tempPath);
            
        } finally {
            if (bufferedInput != null) {
                try { bufferedInput.close(); } catch (IOException ignore) {}
            }
            if (bufferedOutput != null) {
                try { bufferedOutput.close(); } catch (IOException ignore) {}
            }
            if (zipFile != null) {
                try { zipFile.close(); } catch (IOException ignore) {}
            }
        }
    }

    private BufferedInputStream findStreamInZip(ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (entryName.endsWith(".apk")) {
                return new BufferedInputStream(zipFile.getInputStream(entry));
            }
        }
        return null;
    }

    private void copyBufferStream(BufferedInputStream bufferedInput,
                                  BufferedOutputStream bufferedOutput) throws IOException {
        int count;
        byte[] buffer = new byte[8192];
        while ((count = bufferedInput.read(buffer)) > 0) {
            if (isCancelled) {
                return;
            }
            bufferedOutput.write(buffer, 0, count);
        }
        bufferedOutput.flush();
    }

    private boolean isValidApk(String path) {
        try {
            return getContext().getPackageManager().getPackageArchiveInfo(path, 0) != null;
        } catch (Throwable ignore) {
            return false;
        }
    }
}