package com.norman.webviewup.lib.download;

public interface DownloadAction {

    String getUrl();

    void start();

    void stop();

    void delete();

    boolean isCompleted();

    boolean isProcessing();


    void addCallback(Callback callback);

    void removeCallback(Callback callback);

    interface Callback {

        default void onStart() {

        }

        default void onProcess(float percent) {

        }

        default void onComplete(String path) {

        }

        default void onFail(Throwable throwable) {

        }
    }
}
