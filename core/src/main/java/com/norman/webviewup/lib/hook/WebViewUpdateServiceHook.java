package com.norman.webviewup.lib.hook;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;

import com.norman.webviewup.lib.reflect.RuntimeAccess;
import com.norman.webviewup.lib.service.binder.BinderHook;
import com.norman.webviewup.lib.service.interfaces.IServiceManager;
import com.norman.webviewup.lib.service.interfaces.IWebViewProviderResponse;
import com.norman.webviewup.lib.service.interfaces.IWebViewUpdateService;
import com.norman.webviewup.lib.service.binder.ProxyBinder;
import com.norman.webviewup.lib.service.proxy.WebViewUpdateServiceProxy;

import java.util.Map;


public class WebViewUpdateServiceHook extends BinderHook {


    private final Context context;
    private final String webViewPackageName;

    public WebViewUpdateServiceHook(Context context, String packageName) {
        this.context = context;
        this.webViewPackageName = packageName;
    }

    private final WebViewUpdateServiceProxy proxy = new WebViewUpdateServiceProxy() {
        @Override
        protected Object waitForAndGetProvider() {
            Object result = invoke();
            PackageInfo packageInfo;
            try {
                packageInfo = context.getPackageManager()
                        .getPackageInfo(webViewPackageName,
                                PackageManager.GET_SHARED_LIBRARY_FILES
                                        | PackageManager.GET_SIGNATURES
                                        | PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            IWebViewProviderResponse webViewProviderResponse = RuntimeAccess.objectAccess(IWebViewProviderResponse.class, result);
            webViewProviderResponse.setPackageInfo(packageInfo);

            Parcel parcel = Parcel.obtain();
            parcel.writeParcelable((Parcelable) result, 0);
            parcel.setDataPosition(parcel.dataSize()-4);
            parcel.writeInt(0);
            parcel.setDataPosition(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result = parcel.readParcelable(result.getClass().getClassLoader(), result.getClass());
            } else {
                result = parcel.readParcelable(result.getClass().getClassLoader());
            }
            parcel.recycle();

            return result;
        }

        @Override
        protected IBinder asBinder() {
            IBinder proxyBinder = getProxyBinder();
            return  proxyBinder != null?proxyBinder: (IBinder) invoke();
        }

        @Override
        protected boolean isMultiProcessEnabled() {
            return false;
        }
    };


    private Map<String, IBinder> binderCacheMap;

    @Override
    protected IBinder onTargetBinderObtain() {
        IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);
        return serviceManager.getService(IWebViewUpdateService.SERVICE);
    }

    @Override
    protected ProxyBinder onProxyBinderCreate(IBinder binder) {
        IWebViewUpdateService service = RuntimeAccess.staticAccess(IWebViewUpdateService.class);
        IServiceManager serviceManager = RuntimeAccess.staticAccess(IServiceManager.class);

        IInterface oldInterface = service.asInterface(binder);
        proxy.setTarget(oldInterface);
        IInterface proxyInterface = (IInterface) proxy.get();
        ProxyBinder proxyBinder = new ProxyBinder(oldInterface,proxyInterface);

        binderCacheMap = serviceManager.getServiceCache();
        return proxyBinder;
    }

    @Override
    protected void onTargetBinderRestore(IBinder binder) {
        binderCacheMap.put(IWebViewUpdateService.SERVICE, binder);
    }

    @Override
    protected void onProxyBinderReplace(ProxyBinder binder) {
        binderCacheMap.put(IWebViewUpdateService.SERVICE, binder);
    }

}
