package com.norman.webviewup.lib.source;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.util.FileUtils;
import com.norman.webviewup.lib.util.HandlerUtils;

import java.util.HashSet;

public abstract class UpgradeSource {

    private final HashSet<OnPrepareCallback> prepareCallbackSet = new HashSet<>();

    private final Context context;

    private boolean success;

    private boolean running;

    private Throwable errorThrowable;


    public UpgradeSource(@NonNull Context context) {
        this.context = context.getApplicationContext();

    }

    public synchronized boolean isSuccess() {
        return success;
    }

    public synchronized boolean isProcess() {
        return running;
    }

    public synchronized final Throwable getError() {
        return errorThrowable;
    }

    public synchronized void prepare(OnPrepareCallback prepareCallback) {
        prepare(prepareCallback, null);
    }


    public synchronized void prepare(OnPrepareCallback prepareCallback, Object params) {
        if (isSuccess()) {
            HandlerUtils.runInMainThread(() -> {
                prepareCallback.onPrepareSuccess(this);
            });
        } else if (errorThrowable != null) {
            HandlerUtils.runInMainThread(() -> {
                prepareCallback.onPrepareError(this, errorThrowable);
            });
        } else {
            prepareCallbackSet.add(prepareCallback);
            if (!running) {
                running = true;
                try {
                    onPrepare(params);
                } catch (Throwable throwable) {
                    error(throwable);
                }
            }
        }
    }

    protected abstract void onPrepare(Object params);

    protected synchronized final void success() {
        if (success || errorThrowable != null) {
            return;
        }
        success = true;
        running = false;
        onSuccess();
        for (OnPrepareCallback prepareCallback : prepareCallbackSet) {
            HandlerUtils.runInMainThread(() -> {
                prepareCallback.onPrepareSuccess(this);
            });
        }
        prepareCallbackSet.clear();

    }

    protected synchronized final void error(@NonNull Throwable throwable) {
        if (isSuccess() || errorThrowable != null) {
            return;
        }
        errorThrowable = throwable;
        running = false;
        for (OnPrepareCallback prepareCallback : prepareCallbackSet) {
            HandlerUtils.runInMainThread(() -> {
                prepareCallback.onPrepareError(this, throwable);
            });
        }
        prepareCallbackSet.clear();
        onError(throwable);
    }

    protected synchronized final void process(float percent) {
        if (isSuccess() || errorThrowable != null) {
            return;
        }
        for (OnPrepareCallback prepareCallback : prepareCallbackSet) {
            HandlerUtils.runInMainThread(() -> {
                prepareCallback.onPrepareProcess(this, percent);
            });
        }
        onProcess(percent);
    }

    protected void onSuccess() {

    }

    protected void onError(Throwable throwable) {

    }

    protected void onProcess(float percent) {

    }


    public Context getContext() {
        return context;
    }

    public interface OnPrepareCallback {

        void onPrepareSuccess(UpgradeSource webViewSource);

        void onPrepareProcess(UpgradeSource webViewSource, float percent);

        void onPrepareError(UpgradeSource webViewSource, Throwable throwable);
    }
}
