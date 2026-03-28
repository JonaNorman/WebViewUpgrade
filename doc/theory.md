# 背景

Android5.0以后WebView升级需要去Google Play安装APK(图一)，就算安装了以后也不一定能行，像华为、Amazon等特殊机型WebView的Chromium版本一般比较低，只能用它自己的WebView无法用Google的WebView(图二)。

<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/84d98a31d9f04caaa3058a7dee926939~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=672&h=440&s=63148&e=png&b=ffffff" width="400px" >

<img src="https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/053b0a382dce487d8b366a65171e5da1~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=786&h=390&s=79626&e=png&b=f9f8f9" width="400px" >

华为机上WebView内核的Chromium版本低于107无法播放H265视频，为了解决上述问题可以用JS实现H265播放，但是会比较卡，也可以用腾讯的X5内核，但是免费版实际chromium版本是89不支持H265视频，这个时候我就想能不能用App内的WebView APK作为内核，下图是升级WebView内核的前后效果对比

![preview.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ac8b62dbad16462fa8ce80d7ac7c3988~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=400\&h=867\&s=1001406\&e=gif\&f=135\&b=f8f7f4)

升级前在华为机上的系统WebView内核包名是`com.huawei.webview`，版本是14.0.0.331，UserAgent中的Chromium实际版本是99.0.4844.88,如下图所示小于107不支持H265播放

<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b55b684d3bed43a28b24734ae2ec0f2f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=642&h=842&s=96910&e=png&b=0c0c0c" width="400px" >

把WebView内核的包名、版本、包地址传到以下代码升级内核成功后就可以播放H265视频了

<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/695d714e5f804dc99003ea2aebe2e5a5~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1328&h=670&s=174025&e=png&b=151718" width="400px">

WebView内核选择页面如下图所示


<img src="https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/536d1bf17f5d414eab93ae7755776262~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=646&h=952&s=118999&e=png&b=5e5e5e" width="400px" >

升级成功后WebView内核的包名变成了`com.google.android.webview`，UserAgent中的Chromium实际版本也变成了122.0.6261.64

<img src="https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ca5af07abe6a4ea5b510e3d1ad806c6d~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1080&h=2340&s=1351110&e=png&b=030303" width="400px" alt="转存失败，建议直接上传图片文件">

项目地址：[WebViewUpgrade](https://github.com/JonaNorman/WebViewUpgrade)，这个项目是为了解决WebView碎片化而产生，当前处于测试阶段，如果你觉得有所收获，给这个库点个赞吧，你的鼓励是我前进最大的动力，

# 兼容性

Android的设备五花八门，已测试以下功能和机型

## 功能特性

| WebView包名                    | 系统版本                    |
| :--------------------------- | ----------------------- |
| com.google.android.webview   | 122.0.6261.64           |
| com.android.webview          | 113.0.5672.136          |
| com.huawei.webview           | 14.0.0.331              |
| com.android.chrome           | 122.0.6261.43           |
| com.amazon.webview\.chromium | 118-5993-tv.5993.155.51 |

## 机型

| 厂商           | 系统版本 |
| :----------- | ---- |
| 华为Mate30     | 12   |
| 小米10         | 11   |
| VIVO NEX A   | 10   |
| OPPO FIND X5 | 14   |

# 原理介绍


![WebView初始化.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/07497a0023c946eab84acddece079f26~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1121&h=531&s=56015&e=png&b=ffffff)

其实原理很简单，从上图中可以看到WebView初始化的时候会根据**WebViewProviderResponse**(WebViewUpdateService调用`waitForAndGetProvider`)和**PackageInfo**(PacakgeManagerService调用`getPackageInfo`)生成WebViewFactoryProivder，也就是说只要hook这两个方法就行，替换WebViewUpdateService和PacakgeManagerService的调用很简单，其实就是替换Binder的本地接口调用，网上方案很多就不展开了，如果不清楚的可以直接看[代码](https://github.com/JonaNorman/WebViewUpgrade/tree/main/app/src/main/java/com/norman/webviewup/lib/hook)。

替换`waitForAndGetProvider`值得注意的是**WebViewProviderResponse**对象的packageInfo查询时flags要设置成**GET_SHARED_LIBRARY_FILES|GET_SIGNATURES|GET_META_DATA**，后续代码中会用这些数据，不然会崩溃。
![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/77efd2a7ee26491ea67ad763c066298a~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1818&h=1154&s=328400&e=png&b=151718)

替换`getPackageInfo`值得注意的是

1. 用`getPackageArchiveInfo`查询未安装Apk的PackageInfo
2. 有些APK加上**GET_SIGNATURES**查询PackageInfo会找不到
3. `getPackageArchiveInfo`查询出来的PackageInfo不存在nativeLibraryDir，需要手动赋值
4. nativeLibraryDir传入的so路径需要运行时处理器指令集的ABI一样，不然会崩溃
5. 部分手机的`packageInfo.applicationInfo.sourceDir`不存在，需要手动赋值

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/850091a2a8254a07a6a1c494760145e8~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=2048&h=1750&s=531630&e=png&b=151718)

有两个功能还没实现，希望有了解的朋友能提交代码解决这两个问题。

1. 运行时动态切换WebView内核，现在只能在WebView未初始化之前替换，原因是因为会报错`UnsatisfiedLinkError: Shared library "/system/lib64/libwebviewchromium_plat_support.so" already opened by ClassLoader`，WebView内核中会调用System.loadLibrary加载libwebviewchromium_plat_support.so，而系统限制同一个so只能被一个classLoader加载。

2. 不支持多进程功能，如果把WebViewUpdateService的isMultiProcessEnabled设为true，会报错`java.lang.RuntimeException: Illegal meta data value: the child service doesn't exist`, 就算把这个错误解决了，也无法用`Process.startWebView`手动启动WebViewZygote进程
