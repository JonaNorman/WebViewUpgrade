package com.norman.webviewup.lib.download;

public interface DownloadSink {

    DownloadAction createDownload(String url,
                                  String path);
}
