package com.norman.webviewup.lib.reflect;

import android.text.TextUtils;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.ClassType;
import com.norman.webviewup.lib.reflect.annotation.Method;


public abstract class RuntimeProxy {


    private final Object mProxyLock = new Object();

    private final ThreadLocal<ReflectProxy.InvokeContext> mInvokeContextThreadLocal = new ThreadLocal<>();


    private volatile Object target;

    private Object proxy;

    private ReflectProxy reflectProxy;

    private final Class<?> proxyClass;

    public RuntimeProxy() {
        this(null);
    }

    public RuntimeProxy(Class<?> proxyClass) {
        this.proxyClass = proxyClass;
    }

    public void setTarget(Object target) {
        synchronized (mProxyLock){
            this.target = target;
            if (reflectProxy != null){
                reflectProxy.setTarget(target);
            }
        }
    }

    public Object get() throws ReflectException {
        try {
            synchronized (mProxyLock) {
                if (proxy != null) {
                    return  proxy;
                }

                Class<?> reflectClass = proxyClass;
                if (reflectClass == null){
                    ClassType classTypeAnnotation = getClass().getAnnotation(ClassType.class);
                    ClassName classNameAnnotation = getClass().getAnnotation(ClassName.class);
                    Class<?> annotationClass = null;
                    if (classNameAnnotation != null) {
                        annotationClass = Class.forName(classNameAnnotation.value());
                    } else if (classTypeAnnotation != null) {
                        annotationClass = classTypeAnnotation.value();
                    }
                    reflectClass = annotationClass;
                }
                ReflectProxy reflectProxy = new ReflectProxy(reflectClass);
                for (Class<?> clazz = getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                    java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
                    for (java.lang.reflect.Method method : methods) {
                        Method methodAnnotation = method.getAnnotation(Method.class);
                        if (methodAnnotation != null) {
                            String methodName = methodAnnotation.value();
                            if (TextUtils.isEmpty(methodName)) {
                                methodName = method.getName();
                            }
                            ReflectProxy.Invoke invoke = new ReflectProxy.Invoke(
                                    methodName,
                                    method.getParameterTypes()) {
                                @Override
                                protected void onInvoke(ReflectProxy.InvokeContext invokeContext) {
                                    try {
                                        if (invokeContext.target == null) return;
                                        if (!method.isAccessible()) {
                                            method.setAccessible(true);
                                        }
                                        mInvokeContextThreadLocal.set(invokeContext);
                                        Object[] invokeArgs = invokeContext.args;
                                        // 针对 fuzzy 模式：如果代理定义的方法只接受一个 Object[] 参数（例如 Object... args），
                                        // 必须将系统的多参数数组整体包装为一个参数传递，否则会报 Wrong number of arguments
                                        if (methodAnnotation.fuzzy() && method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == Object[].class) {
                                            invokeArgs = new Object[]{invokeContext.args};
                                        }
                                        Object result = method.invoke(RuntimeProxy.this, invokeArgs);
                                        invokeContext.setResult(result);
                                    } catch (Throwable e) {
                                        throw new ReflectException(e);
                                    } finally {
                                        mInvokeContextThreadLocal.set(null);
                                    }
                                }
                            };
                            reflectProxy.addInvoke(invoke, false, methodAnnotation.fuzzy());
                        }
                    }
                }
                reflectProxy.setTarget(target);
                proxy = reflectProxy.newProxyInstance();
                this.reflectProxy = reflectProxy;
                return  proxy;
            }
        } catch (Throwable throwable) {
            throw new ReflectException(throwable);
        }
    }


    protected Object invoke() {
        ReflectProxy.InvokeContext invokeContext = mInvokeContextThreadLocal.get();
        if (invokeContext == null) {
            return null;
        }
        return invokeContext.invoke();
    }

    protected Object getTarget() {
        ReflectProxy.InvokeContext invokeContext = mInvokeContextThreadLocal.get();
        if (invokeContext == null) {
            return null;
        }
        return invokeContext.target;
    }
}
