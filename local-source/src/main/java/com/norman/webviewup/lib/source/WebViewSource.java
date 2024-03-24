package com.norman.webviewup.lib.source;

import androidx.annotation.NonNull;

import java.util.HashSet;

public abstract class WebViewSource {

    private final HashSet<OnPrepareCallback> prepareCallbackSet = new HashSet<>();

    private boolean success;

    private boolean running;

    private Throwable errorThrowable;



    public synchronized final boolean isSuccess() {
        return success;
    }

    public synchronized void prepare(OnPrepareCallback prepareCallback) {
        if (success) {
            prepareCallback.onPrepareSuccess(this);
        } else if (errorThrowable != null) {
            prepareCallback.onPrepareError(this, errorThrowable);
        } else {
            prepareCallbackSet.add(prepareCallback);
            if (!running) {
                running = true;
                try {
                    onPrepare();
                } catch (Throwable throwable) {
                    error(throwable);
                }
            }
        }
    }

    protected abstract void onPrepare();

    protected synchronized final void success() {
        if (success || errorThrowable != null) {
            return;
        }
        success = true;
        for (OnPrepareCallback prepareCallback : prepareCallbackSet) {
            prepareCallback.onPrepareSuccess(this);
        }
        prepareCallbackSet.clear();
    }

    protected synchronized final void error(@NonNull Throwable throwable) {
        if (success || errorThrowable != null) {
            return;
        }
        errorThrowable = throwable;
        for (OnPrepareCallback prepareCallback : prepareCallbackSet) {
            prepareCallback.onPrepareError(this, throwable);
        }
        prepareCallbackSet.clear();
    }


    public interface OnPrepareCallback {

        void onPrepareSuccess(WebViewSource webViewSource);

        void onPrepareError(WebViewSource webViewSource, Throwable throwable);
    }
}
