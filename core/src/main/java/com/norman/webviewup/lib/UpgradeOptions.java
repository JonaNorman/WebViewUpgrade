package com.norman.webviewup.lib;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.download.DownloadSink;

public class UpgradeOptions {

    public final DownloadSink downloaderSink;
    public final Application context;
    public final String url;


    private UpgradeOptions(UpgradeOptions.Builder builder) {
        this.context = builder.context;
        this.url = builder.url;
    }

    public static class Builder {
        Application context;
        String url;


        public Builder(@NonNull Context context, @NonNull String packageName, @NonNull String url, @NonNull String version, @NonNull DownloadSink downloaderSink) {
            this.downloaderSink = downloaderSink;
            this.context = (Application) context.getApplicationContext();
            this.url = url;
        }

        private Builder(UpgradeOptions options) {
            downloaderSink = options.downloaderSink;
            context = options.context;
            url = options.url;
        }


        public Builder setDownloaderSink(DownloadSink downloaderSink) {
            this.downloaderSink = downloaderSink;
            return this;
        }

        public Builder setContext(Context context) {
            this.context = (Application) context.getApplicationContext();
            return this;
        }


        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public UpgradeOptions build() {
            return new UpgradeOptions(this);
        }
    }


}
