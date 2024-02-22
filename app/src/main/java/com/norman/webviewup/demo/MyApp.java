package com.norman.webviewup.demo;

import android.app.Application;

import com.arialyy.aria.core.Aria;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Aria.init(this);
    }
}
