package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;

@ClassName("android.content.pm.ApplicationInfo")
public interface IApplicationInfo {
    @Field("primaryCpuAbi")
    void setPrimaryCpuAbi(String cpuAbi);

    @Field("nativeLibraryRootDir")
    void setNativeLibraryRootDir(String nativeLibraryRootDir);
}