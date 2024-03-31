package com.norman.webviewup.lib.source.download;

import android.content.Context;

import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.config.DownloadConfig;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadReceiver;
import com.arialyy.aria.core.download.DownloadTaskListener;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.task.DownloadTask;
import com.norman.webviewup.lib.source.UpgradePathSource;
import com.norman.webviewup.lib.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UpgradeDownloadSource extends UpgradePathSource implements DownloadTaskListener {

    public static final int MAX_DOWNLOAD_THREAD_NUM = 10;
    private final String url;
    private final int threadNum;

    private DownloadReceiver downloadReceiver;

    private String tempPath;

    private DownloadEntity downloadEntity;


    public UpgradeDownloadSource(Context context, String url, File file, int threadNum) {
        super(context,file.getPath());
        this.url = url;
        this.threadNum = threadNum;
    }

    public UpgradeDownloadSource(Context context, String url, File file) {
        this(context, url, file, MAX_DOWNLOAD_THREAD_NUM);
    }


    @Override
    protected void onPrepare(Object params) {
        AriaManager ariaManager = Aria.init(getContext());
        DownloadConfig downloadConfig = ariaManager.getDownloadConfig();
        downloadConfig.setThreadNum(threadNum);
        downloadReceiver = Aria.download(this);
        downloadEntity = downloadReceiver.getFirstDownloadEntity(url);
        tempPath = getApkPath() + ".tmp";
        this.downloadReceiver.register();
        if (downloadEntity == null ||
                downloadEntity.getState() == IEntity.STATE_CANCEL) {
            FileUtils.createFile(tempPath);
            long taskId = downloadReceiver
                    .load(url)
                    .setFilePath(tempPath)
                    .ignoreCheckPermissions()
                    .ignoreFilePathOccupy()
                    .create();
            downloadEntity = downloadReceiver.getDownloadEntity(taskId);
        } else if (downloadEntity.getState() == IEntity.STATE_WAIT
                || downloadEntity.getState() == IEntity.STATE_OTHER
                || downloadEntity.getState() == IEntity.STATE_FAIL
                || downloadEntity.getState() == IEntity.STATE_STOP) {
            downloadReceiver
                    .load(downloadEntity.getId())
                    .ignoreCheckPermissions()
                    .resume();
        } else if (downloadEntity.getState() == IEntity.STATE_COMPLETE) {
            copyApk();
        }
    }

    private void copyApk() {
        new Thread(() -> {
            BufferedInputStream bufferedInput = null;
            BufferedOutputStream bufferedOutput = null;
            ZipFile zipFile = null;
            try {
                if (isValidApk(tempPath)) {
                    bufferedInput = new BufferedInputStream(new FileInputStream(tempPath));
                } else {
                    zipFile = new ZipFile(tempPath);
                    bufferedInput = findStreamInZip(zipFile);
                }
                Objects.requireNonNull(bufferedInput);
                FileUtils.createFile(getApkPath());
                bufferedOutput = new BufferedOutputStream(new FileOutputStream(getApkPath()));
                copyBufferStream(bufferedInput, bufferedOutput);
                success();
                deleteDownload();
            } catch (Throwable e) {
                FileUtils.delete(getApkPath());
                error(e);
            } finally {
                try {
                    if (bufferedInput != null) {
                        bufferedInput.close();
                    }
                } catch (IOException ignore) {

                }
                try {
                    if (bufferedOutput != null) {
                        bufferedOutput.close();
                    }
                } catch (IOException ignore) {

                }

                try {
                    if (zipFile != null) {
                        zipFile.close();
                    }
                } catch (IOException ignore) {

                }
            }

        }).start();
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
        int readCount = 0;
        int availableByteCount = bufferedInput.available();
        byte[] buffer = new byte[8192];
        while ((count = bufferedInput.read(buffer)) > 0) {
            bufferedOutput.write(buffer, 0, count);
            readCount = readCount + count;
            process(0.05f * readCount / availableByteCount + 0.95f);
        }
        bufferedOutput.flush();
    }

    private void deleteDownload() {
        try {
            downloadReceiver
                    .load(downloadEntity.getId())
                    .ignoreCheckPermissions()
                    .cancel(true);
        } catch (Throwable ignore) {

        }
    }


    @Override
    public void onWait(DownloadTask task) {

    }

    @Override
    public void onPre(DownloadTask task) {
        downloadEntity = task.getDownloadEntity();
    }

    @Override
    public void onTaskPre(DownloadTask task) {

    }

    @Override
    public void onTaskResume(DownloadTask task) {

    }

    @Override
    public void onTaskStart(DownloadTask task) {

    }

    @Override
    public void onTaskStop(DownloadTask task) {
        downloadEntity = task.getDownloadEntity();
    }

    @Override
    public void onTaskCancel(DownloadTask task) {
        downloadEntity = task.getDownloadEntity();
    }

    @Override
    public void onTaskFail(DownloadTask task, Exception e) {
        if (e == null){
            error(new RuntimeException("download fail"));
        }else {
            error(e);
        }
    }

    @Override
    public void onTaskComplete(DownloadTask task) {
        copyApk();
    }

    @Override
    public void onTaskRunning(DownloadTask task) {
        float percent = task.getPercent() / 100.0f * 0.95f;
        process(percent);
    }


    @Override
    public void onNoSupportBreakPoint(DownloadTask task) {

    }


    private boolean isValidApk(String path) {
        try {
            return getContext().getPackageManager().getPackageArchiveInfo(path, 0) != null;
        } catch (Throwable ignore) {

        }
        return false;
    }

}
