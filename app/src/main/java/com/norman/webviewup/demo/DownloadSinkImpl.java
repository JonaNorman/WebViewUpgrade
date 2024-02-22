package com.norman.webviewup.demo;

import android.util.Log;

import com.arialyy.aria.core.Aria;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadReceiver;
import com.arialyy.aria.core.download.DownloadTaskListener;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.task.DownloadTask;
import com.norman.webviewup.lib.download.DownloadAction;
import com.norman.webviewup.lib.download.DownloaderSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DownloadSinkImpl implements DownloaderSink {

    private final List<DownloadActionImpl> downloadActionList = new ArrayList<>();

    @Override
    public synchronized DownloadAction createDownload(String url, String path) {
        DownloadActionImpl action = null;
        for (DownloadActionImpl downloadAction : downloadActionList) {
            if (Objects.equals(downloadAction.url, url)) {
                action = downloadAction;
                break;
            }
        }
        if (action != null) {
            return action;
        }
        action = new DownloadActionImpl(url, path);
        downloadActionList.add(action);
        return action;
    }


    static class DownloadActionImpl implements DownloadAction, DownloadTaskListener {

        private final String url;
        private final DownloadReceiver downloadReceiver;

        private final List<DownloadAction.Callback> callbackList = new ArrayList<>();

        private final String path;

        private long taskId;

        private DownloadEntity downloadEntity;

        private boolean restart;


        public DownloadActionImpl(String url, String path) {
            this.url = url;
            this.path = path;
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
        public String getUrl() {
            return url;
        }

        @Override
        public synchronized void start() {
            if (downloadEntity != null && !Objects.equals(downloadEntity.getUrl(), url)) {
                downloadReceiver
                        .load(downloadEntity.getId())
                        .cancel(true);
                downloadEntity = null;
                taskId = 0;
            }
            if (downloadEntity == null ||
                    downloadEntity.getState() == IEntity.STATE_CANCEL) {
                taskId = downloadReceiver
                        .load(url)
                        .setFilePath(path)
                        .ignoreCheckPermissions()
                        .ignoreFilePathOccupy()
                        .create();
                downloadEntity = downloadReceiver.getDownloadEntity(taskId);
            } else if (downloadEntity.getState() == IEntity.STATE_WAIT
                    ||downloadEntity.getState() == IEntity.STATE_OTHER
                    || downloadEntity.getState() == IEntity.STATE_FAIL
                    || downloadEntity.getState() == IEntity.STATE_STOP) {
                downloadReceiver
                        .load(taskId)
                        .ignoreCheckPermissions()
                        .resume();
            } else if (downloadEntity.getState() == IEntity.STATE_COMPLETE) {
                downloadReceiver
                        .load(taskId)
                        .ignoreCheckPermissions()
                        .cancel(true);
                downloadEntity = null;
                restart = true;
            }
        }

        @Override
        public synchronized void stop() {
            if (downloadEntity == null) {
                return;
            }
            int state = downloadEntity.getState();
            if (state == IEntity.STATE_PRE
                    || state == IEntity.STATE_POST_PRE
                    || state == IEntity.STATE_RUNNING) {
                downloadReceiver
                        .load(taskId)
                        .stop();
            }
        }

        @Override
        public synchronized boolean isCompleted() {
            if (downloadEntity == null) {
                return false;
            }
            int state = downloadEntity.getState();
            return Objects.equals(downloadEntity.getUrl(), url)
                    && state == IEntity.STATE_COMPLETE;
        }

        @Override
        public synchronized boolean isProcessing() {
            if (downloadEntity == null) {
                return false;
            }
            int state = downloadEntity.getState();
            return Objects.equals(downloadEntity.getUrl(), url)
                    && (state == IEntity.STATE_WAIT
                    || state == IEntity.STATE_PRE
                    || state == IEntity.STATE_POST_PRE
                    || state == IEntity.STATE_RUNNING);
        }

        @Override
        public synchronized void addCallback(Callback callback) {
            if (callbackList.contains(callback)){
                return;
            }
            callbackList.add(callback);
        }

        @Override
        public synchronized void removeCallback(Callback callback) {
            if (!callbackList.contains(callback)){
                return;
            }
            callbackList.remove(callback);
        }

        @Override
        public void onWait(DownloadTask task) {
        }

        @Override
        public synchronized void onPre(DownloadTask task) {
            downloadEntity = task.getDownloadEntity();

        }

        @Override
        public void onTaskPre(DownloadTask task) {
            Log.v("aa", "a");
        }

        @Override
        public synchronized void onTaskResume(DownloadTask task) {
            for (Callback callback : callbackList) {
                callback.onStart();;
            }
        }

        @Override
        public synchronized void onTaskStart(DownloadTask task) {
            for (Callback callback : callbackList) {
                callback.onStart();;
            }
        }

        @Override
        public synchronized void onTaskStop(DownloadTask task) {
            downloadEntity = task.getDownloadEntity();
        }

        @Override
        public synchronized void onTaskCancel(DownloadTask task) {
            downloadEntity = task.getDownloadEntity();
            if (restart){
                taskId = downloadReceiver
                        .load(url)
                        .setFilePath(path)
                        .ignoreCheckPermissions()
                        .ignoreFilePathOccupy()
                        .create();
                downloadEntity = downloadReceiver.getDownloadEntity(taskId);
                restart = false;
            }

        }

        @Override
        public synchronized void onTaskFail(DownloadTask task, Exception e) {
            for (Callback callback : callbackList) {
                callback.onFail(e);;
            }
        }

        @Override
        public synchronized void onTaskComplete(DownloadTask task) {

            for (Callback callback : callbackList) {
                callback.onComplete(task.getFilePath());
            }
        }

        @Override
        public synchronized void onTaskRunning(DownloadTask task) {
            float percent = task.getPercent() / 100.0f;
            for (Callback callback : callbackList) {
                callback.onProcess(percent);
            }
        }

        @Override
        public void onNoSupportBreakPoint(DownloadTask task) {


        }
    }
}
