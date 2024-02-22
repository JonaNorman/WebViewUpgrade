package com.norman.webviewup.lib;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.download.DownloaderSink;

public class UpgradeOptions {

    public final DownloaderSink downloaderSink;
    public final Application context;
    public final String packageName;
    public final String url;
    public final String versionName;


    private UpgradeOptions(UpgradeOptions.Builder builder) {
        this.downloaderSink = builder.downloaderSink;
        this.context = builder.context;
        this.packageName = builder.packageName;
        this.url = builder.url;
        this.versionName = builder.versionName;
    }

    public static class Builder {
        public DownloaderSink downloaderSink;
        public Application context;
        public String packageName;
        public String url;
        public String versionName;


        public Builder(@NonNull Context context, @NonNull String packageName, @NonNull String url, @NonNull String version, @NonNull DownloaderSink downloaderSink) {
            this.downloaderSink = downloaderSink;
            this.context = (Application) context.getApplicationContext();
            this.packageName = packageName;
            this.url = url;
            this.versionName = version;
        }

        private Builder(UpgradeOptions options) {
            downloaderSink = options.downloaderSink;
            context = options.context;
            packageName = options.packageName;
            url = options.url;
            versionName = options.versionName;
        }


        public Builder setDownloaderSink(DownloaderSink downloaderSink) {
            this.downloaderSink = downloaderSink;
            return this;
        }

        public Builder setContext(Context context) {
            this.context = (Application) context.getApplicationContext();
            return this;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setVersionName(String versionName) {
            this.versionName = versionName;
            return this;
        }

        public UpgradeOptions build() {
            return new UpgradeOptions(this);
        }
    }


}
