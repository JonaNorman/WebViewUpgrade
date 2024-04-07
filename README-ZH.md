# WebViewUpgrade
简体中文 | [English](./README.md)

还在经受WebView差异化带来的兼容问题，还在为腾讯X5内核收费所困扰，这个库也许就能轻松解决这些问题，它实现了Android免安装升级WebView内核的功能。如果你觉得有所收获，给这个库点个Star吧，你的鼓励是我前进最大的动力，这年头写代码不就这点追求嘛😊。

Android5.0以后WebView升级需要去Google Play安装APK，就算安装了以后也不一定能行，像华为、Amazon等特殊机型WebView的Chromium版本一般比较低，只能用它自己的WebView无法用Google的WebView。

我就遇到了华为机上因为WebView内核的Chromium版本低于107无法播放H265视频的情况，为了解决上述问题可以用JS实现H265播放，但是会比较卡，这个时候我就想能不能让WebView用应用内的APK作为内核，下图是升级WebView内核的前后效果对比

![preview](preview/preview.gif)

升级前在华为机上的系统WebView内核包名是`com.huawei.webview`，版本是14.0.0.331，UserAgent中的Chromium实际版本是99.0.4844.88,如下图所示小于107不支持H265播放

<img src="/preview/webview_can_not_play_h265.jpg" width="400px">

把WebView内核的包名、版本、包地址传到以下代码升级内核成功后就可以播放H265视频了

<img src="/preview/upgrade_code.png" width="400px">

WebView内核选择页面如下图所示

<img src="/preview/choose_webview.jpg" width="400px">

升级成功的WebView内涵的包名变成了`com.google.android.webview`，UserAgent中的Chromium实际版本也变成了122.0.6261.64

<img src="/preview/webview_can_play_h265.jpg" width="400px">

# 使用
```gradle
implementation 'io.github.jonanorman.android.webviewup:core:0.1.0'
implementation 'io.github.jonanorman.android.webviewup:download-source:0.1.0'
```

```java
            UpgradeDownloadSource  upgradeSource = new UpgradeDownloadSource(
                    context,
                    url,
                    file
            );
            WebViewUpgrade.upgrade(upgradeSource);
```

# 兼容性

Android的设备五花八门，已测试以下功能和机型，欢迎大家提issue和Merge Request加入到这个项目中来，有疑惑可以扫码加群，如果二维码显示过期了，可以搜索微信号JonaNorman加我个人微信拉你进群(请备注WebView升级)

<img src="/preview/chat.jpg" width="300px">


## 功能特性
| WebView包名    | 系统版本 |
|:-------------| ----- |
|com.google.android.webview     | 122.0.6261.64  |
| com.android.webview       | 113.0.5672.136      |
| com.huawei.webview   | 14.0.0.331     |
| com.android.chrome | 122.0.6261.43     |
| com.amazon.webview.chromium | 118-5993-tv.5993.155.51   |

## 机型
| 厂商         | 系统版本 |
| :----------- | -------- |
| 华为Mate30   | 12       |
| 小米10       | 11       |
| VIVO NEX A   | 10       |
| OPPO FIND X5 | 14       |

**待开发功能**

- [ ] 多进程
- [ ] 动态切换

原理介绍: [地址](https://juejin.cn/post/7340900764364472332#heading-4)


# ⭐ star历史

![Star History Chart](https://api.star-history.com/svg?repos=JonaNorman/WebViewUpgrade&type=Date)


# 特别感谢

| Stargazers                                                                                                 | Forkers                                                                                                                 |
|---------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| [![Stargazers repo roster for @JonaNorman/HDRSample](https://reporoster.com/stars/JonaNorman/WebViewUpgrade)](https://github.com/JonaNorman/WebViewUpgrade/stargazers)                                          | [![Forkers repo roster for @JonaNorman/HDRSample](https://reporoster.com/forks/JonaNorman/WebViewUpgrade)](https://github.com/JonaNorman/WebViewUpgrade/network/members)                            |
