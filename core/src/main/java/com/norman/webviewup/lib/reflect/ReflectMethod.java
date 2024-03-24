package com.norman.webviewup.lib.reflect;

import android.text.TextUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class ReflectMethod {

    private final Object mMethodLock = new Object();
    private String mName;

    private Class<?> mClass;
    private String mClassName;

    private Object mObject;

    private Class<?>[] mParameterTypes;

    private Method mMethod;

    private Method assessMethod;

    private int staticType;


    public ReflectMethod(Method method) {
        this(null,method);
    }

    public ReflectMethod(Object object, Method method) {
        this.mMethod = method;
        this.mParameterTypes = method.getParameterTypes();
        if (object != null) {
            if (object instanceof Class<?>) {
                this.mClass = (Class<?>) object;
            } else {
                this.mObject = object;
                this.mClass = object.getClass();
            }
        }
    }

    public ReflectMethod(String className, String name, Class<?>... parameterTypes) {
        this.mName = name;
        this.mClassName = className;
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }

    public ReflectMethod(Class<?> cls, String name, Class<?>... parameterTypes) {
        this.mName = name;
        this.mClass = cls;
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }

    public ReflectMethod(Object object, String name, Class<?>... parameterTypes) {
        this.mName = name;
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
        if (object != null) {
            if (object instanceof Class<?>) {
                this.mClass = (Class<?>) object;
            } else {
                this.mObject = object;
                this.mClass = object.getClass();
            }
        }
    }

    public Object invoke(Object... args) throws ReflectException {
        synchronized (mMethodLock) {
            prepareMethod();
            try {
                return assessMethod.invoke(mObject, args);
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }

    public void setStaticType(int staticValue) {
        this.staticType = staticValue;
    }

    private void prepareMethod() throws ReflectException {
        synchronized (mMethodLock) {
            try {
                if (assessMethod != null) {
                    return;
                }
                Method findMethod = mMethod;
                if (findMethod == null) {
                    String className = null;
                    String filedName = mName;
                    if (!TextUtils.isEmpty(mName)) {
                        int lastDotIndex = mName.lastIndexOf(".");
                        if (lastDotIndex >= 0 && lastDotIndex < mName.length() - 1) {
                            className = mName.substring(0, lastDotIndex);
                            filedName = mName.substring(lastDotIndex + 1);
                        }
                    }
                    if (className != null){
                        try {
                            Class<?> relfectClass = Class.forName(className);
                            findMethod = findMethod(relfectClass, filedName);
                        }catch (Throwable ignore){

                        }
                    }
                    if (findMethod == null){
                        Class<?> findClass = null;
                        if (mClass != null) {
                            findClass = mClass;
                        } else if (mClassName != null) {
                            findClass = Class.forName(mClassName);
                        }
                        findMethod = findMethod(findClass, filedName);
                    }
                }
                if (findMethod == null) {
                    throw new NoSuchMethodException("can not find method: " + mName);
                }
                int modifiers = findMethod.getModifiers();
                if (!Modifier.isPublic(modifiers) ||
                        !Modifier.isPublic(findMethod
                                .getDeclaringClass()
                                .getModifiers())) {
                    if (!findMethod.isAccessible()) {
                        findMethod.setAccessible(true);
                    }
                }
                assessMethod = findMethod;
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }

        }
    }

    private Method findMethod(Class<?> findClass, String filedName) {
        for (Class<?> clazz = findClass; clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Method method = clazz.getDeclaredMethod(filedName, mParameterTypes);
                boolean staticModifiers = Modifier.isStatic(method.getModifiers());
                if ((staticType == 0 && staticModifiers) || (staticType == 1 && !staticModifiers)) {
                    continue;
                }
                return  method;
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

}
