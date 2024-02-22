package com.norman.webviewup.lib.download;

public interface DownloaderSink {

    DownloadAction createDownload(String url,
                                  String path);
}
