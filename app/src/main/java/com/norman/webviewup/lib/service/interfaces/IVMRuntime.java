package com.norman.webviewup.lib.service.interfaces;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.Field;
import com.norman.webviewup.lib.reflect.annotation.Method;

@ClassName("dalvik.system.VMRuntime")
public interface IVMRuntime {
    @Method(value = "getRuntime", type = Field.STATIC)
    Object getRuntime();

    @Method(value = "is64Bit")
    boolean is64Bit();


}
