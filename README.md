# WebViewUpgrade

English | [简体中文](./README-ZH.md)

[![Stars](https://img.shields.io/github/stars/JonaNorman/WebViewUpgrade?style=flat-square&logo=github)](https://github.com/JonaNorman/WebViewUpgrade/stargazers)
[![Forks](https://img.shields.io/github/forks/JonaNorman/WebViewUpgrade?style=flat-square&logo=github)](https://github.com/JonaNorman/WebViewUpgrade/network/members)
[![Issues](https://img.shields.io/github/issues/JonaNorman/WebViewUpgrade?style=flat-square)](https://github.com/JonaNorman/WebViewUpgrade/issues)

This library implements the functionality of upgrading the WebView kernel on Android without installation.

### Application scope

The library is **integrated into your app** and only changes how **your app** resolves its WebView implementation. It does **not** replace the system WebView or affect other installed applications.

### WebView APK archive

For vendor builds, architectures, and version metadata (Google, AOSP, Chrome as provider, Huawei, Amazon, etc.), see the companion repository **[WebViewPackage](https://github.com/JonaNorman/WebViewPackage)** — it ships `webview_packages.json` for scripted downloads and CI.

After Android 5.0, upgrading WebView requires installing an APK from Google Play, and even after installation, it may not work as expected. For special device models like Huawei and Amazon, the Chromium version of WebView is generally lower, making it impossible to use Google's WebView instead of the device's own WebView.

I encountered a situation on Huawei devices where H265 video playback was not possible due to the Chromium version of the WebView kernel being lower than 107. To address this issue, H265 playback can be implemented using JavaScript, but this approach may lead to performance issues. At this point, I wondered if WebView could utilize the APK within the application as its kernel. The image below shows the before and after effects of upgrading the WebView kernel:

![preview](preview/preview.gif)

Before the upgrade, the WebView kernel package name on Huawei devices was `com.huawei.webview`, with a version of 14.0.0.331. The Chromium version in the UserAgent was actually 99.0.4844.88, as shown in the image below, which does not support H265 playback as it is less than 107:

<img src="/preview/webview_can_not_play_h265.jpg" width="400px">

The demo **System environment** screen shows the system WebView, installed Chrome, the kernel in use by the host app, upgrade progress, and actions:

<img src="/preview/system_environment_en.jpg" width="400px">

**Choose / change kernel** opens a list with bundled, installed, downloaded, and online sources:

<img src="/preview/choose_webview_en.jpg" width="400px">

After a successful upgrade, the package name of the WebView kernel changes to `com.google.android.webview`, and the Chromium version in the UserAgent also changes to 122.0.6261.64:

<img src="/preview/webview_can_play_h265.jpg" width="400px">

# implementation

```gradle
implementation 'io.github.jonanorman.android.webviewup:core:0.1.0'
```

Everything ships in **`core`** — add that dependency only.

There are four **UpgradeSource** types: `UpgradeDownloadSource` (HTTP(S) URL), `UpgradeFileSource` (local `.apk` file), `UpgradeAssetSource` (`.apk` under `assets/`), `UpgradePackageSource` (installed package name, e.g. Chrome). Construct one as `source`, then:

```java
WebViewUpgrade.upgrade(source);
```

Optional: `WebViewUpgrade.addUpgradeCallback(...)` before `upgrade` for progress / success / error.

## Compatibility

Android devices vary greatly. The following features and device models have been tested. Contributions through issue submissions and Merge Requests to this project are welcomed.

### Support scope

- **Multi-process WebView** — supported.
- **Android 15 and 16** — supported as target / runtime platforms (always validate on your own devices and ROMs).
- **Kernel sources** — local file (`UpgradeFileSource`), assets (`UpgradeAssetSource`), **installed** package e.g. Chrome (`UpgradePackageSource`), or remote URL (`UpgradeDownloadSource`, included in `core`).

### Upgrade timing (important)

WebView binds to a provider the first time it is created in the process; **switching to another kernel** also cannot hot-swap in the same process — **restart the app** (exit and cold-start) before the new kernel applies. For the **first** upgrade to take effect **without** a restart, perform it **before** any WebView is created (e.g. before the user opens a screen that instantiates WebView). If WebView was already used and you upgrade or switch afterward, **fully cold-start the app** so the new implementation can take effect.

### Chrome as provider

Only a **single, full (monolithic) Chrome APK** can be used as the kernel source. **Split / multi-APK installs** produced by Play app bundles (base + splits) are **not** supported for this use case — use a standalone full APK matching the archive in [WebViewPackage](https://github.com/JonaNorman/WebViewPackage).

### Device Models

| Manufacturer  | System Version |
| :------------ | -------------- |
| Huawei Mate30 | 12             |
| Xiaomi 10     | 11             |
| VIVO NEX A    | 10             |
| OPPO FIND X5  | 14             |

# ⭐ Star History

![Star History Chart](https://api.star-history.com/svg?repos=JonaNorman/WebViewUpgrade&type=Date)
