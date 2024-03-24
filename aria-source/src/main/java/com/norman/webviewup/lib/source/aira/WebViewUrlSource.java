package com.norman.webviewup.lib.source.aira;

import android.content.Context;
import android.text.TextUtils;

import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadReceiver;
import com.arialyy.aria.core.download.DownloadTaskListener;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.task.DownloadTask;
import com.norman.webviewup.lib.UpgradeDirectory;
import com.norman.webviewup.lib.download.DownloadAction;
import com.norman.webviewup.lib.source.WebViewPathSource;
import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.Md5Utils;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class WebViewUrlSource extends WebViewPathSource implements DownloadTaskListener {

    private final String url;

    private final String path;

    private final DownloadReceiver downloadReceiver;

    private long taskId;

    private DownloadEntity downloadEntity;

    public WebViewUrlSource(Context context,String url) {
        this(context,url,null);
    }

    public WebViewUrlSource(Context context,String url, String path) {
        this.url = url;
        if (TextUtils.isEmpty(path)  && !TextUtils.isEmpty(url)) {
            File apkFile = UpgradeDirectory.getApkFile(context, Md5Utils.getMd5(url));
            this.path = apkFile.getPath();
        } else {
            this.path = path;
        }
        this.downloadReceiver = Aria.download(this);
        this.downloadReceiver.register();

        List<DownloadEntity> downloadEntityList = this.downloadReceiver.getDownloadEntity(url);
        if (downloadEntityList != null) {
            for (DownloadEntity entity : downloadEntityList) {
                if (Objects.equals(entity.getFilePath(), path)) {
                    downloadEntity = entity;
                    break;
                }
            }
        }
        if (downloadEntity != null) {
            taskId = downloadEntity.getId();
        }
    }


    @Override
    protected void onPrepare() {
        if (downloadEntity == null ||
                downloadEntity.getState() == IEntity.STATE_CANCEL) {
            FileUtils.makeDirectory(path);
            taskId = downloadReceiver
                    .load(url)
                    .setFilePath(path)
                    .ignoreCheckPermissions()
                    .ignoreFilePathOccupy()
                    .create();
            downloadEntity = downloadReceiver.getDownloadEntity(taskId);
        } else if (downloadEntity.getState() == IEntity.STATE_WAIT
                || downloadEntity.getState() == IEntity.STATE_OTHER
                || downloadEntity.getState() == IEntity.STATE_FAIL
                || downloadEntity.getState() == IEntity.STATE_STOP) {
            downloadReceiver
                    .load(taskId)
                    .ignoreCheckPermissions()
                    .resume();
        } else if (downloadEntity.getState() == IEntity.STATE_COMPLETE) {
            success();
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
        for (DownloadAction.Callback callback : callbackList) {
            callback.onFail(e);
        }
    }

    @Override
    public void onTaskComplete(DownloadTask task) {
        for (DownloadAction.Callback callback : callbackList) {
            callback.onComplete(task.getFilePath());
        }
    }

    @Override
    public void onTaskRunning(DownloadTask task) {
        float percent = task.getPercent() / 100.0f;
        for (DownloadAction.Callback callback : callbackList) {
            callback.onProcess(percent);
        }
    }

    @Override
    public void onNoSupportBreakPoint(DownloadTask task) {

    }

    @Override
    public String getPath() {
        return path;
    }
}
