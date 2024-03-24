package com.norman.webviewup.lib.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class ReflectConstructor {

    private final Object mConstructorLock = new Object();

    private String mClassName;

    private Class<?> mClass;

    private Constructor<?> mConstructor;

    private Class<?>[] mParameterTypes;


    private Constructor<?> assessConstructor;

    public ReflectConstructor(Constructor<?> constructor) {
        this.mConstructor = constructor;
        this.mParameterTypes = constructor.getParameterTypes();
    }

    public ReflectConstructor(String className, Class<?>... parameterTypes) {
        this.mClassName = className;
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }

    public ReflectConstructor(Class<?> cls, Class<?>... parameterTypes) {
        this.mClass = cls;
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }

    public ReflectConstructor(Object obj, Class<?>... parameterTypes) {
        if (obj != null) {
            if (obj instanceof Class<?>) {
                this.mClass = (Class<?>) obj;
            } else {
                this.mClass = obj.getClass();
            }
        }
        this.mParameterTypes = parameterTypes == null ? new Class<?>[0] : parameterTypes;
    }


    public Object newInstance(Object... args) throws ReflectException {
        synchronized (mConstructorLock) {
            prepareConstructor();
            try {
                return assessConstructor.newInstance(args);
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }


    private void prepareConstructor() throws ReflectException {
        synchronized (mConstructorLock) {
            try {
                if (assessConstructor != null) {
                    return;
                }
                Constructor<?> findConstructor = mConstructor;
                if (findConstructor == null) {
                    Class<?> findClass = null;
                    if (mClass != null) {
                        findClass = mClass;
                    } else if (mClassName != null) {
                        findClass = Class.forName(mClassName);
                    }
                    if (findClass != null) {
                        findConstructor = findClass.getDeclaredConstructor(mParameterTypes);
                    }
                }
                if (findConstructor == null) {
                    throw new NoSuchMethodException("can not find constructor");
                }
                int modifiers = findConstructor.getModifiers();
                if (!Modifier.isPublic(modifiers) ||
                        !Modifier.isPublic(findConstructor
                                .getDeclaringClass()
                                .getModifiers())) {
                    if (!findConstructor.isAccessible()) {
                        findConstructor.setAccessible(true);
                    }
                }
                assessConstructor = findConstructor;
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }
}
