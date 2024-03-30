package com.norman.webviewup.lib;

public class WebViewReplaceException extends Exception {

    public WebViewReplaceException(String message) {
        super(message);
    }

    public WebViewReplaceException(String message, Throwable cause) {
        super(message, cause);
        setStackTrace(cause.getStackTrace());
    }


}
