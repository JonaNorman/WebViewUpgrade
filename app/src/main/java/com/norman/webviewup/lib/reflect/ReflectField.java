package com.norman.webviewup.lib.reflect;

import android.text.TextUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class ReflectField {

    private final Object mFiledLock = new Object();
    private String mName;

    private String mClassName;
    private Class<?> mClass;

    private Object mObject;

    private Field mFiled;
    private Field assessfield;

    private int staticType;

    public ReflectField(Field field) {
        this(null,field);
    }

    public ReflectField(Object object, Field field) {
        this.mFiled = field;
        if (object != null) {
            if (object instanceof Class<?>) {
                this.mClass = (Class<?>) object;
            } else {
                this.mObject = object;
                this.mClass = object.getClass();
            }
        }
    }

    public ReflectField(String className, String name) {
        this.mName = name;
        this.mClassName = className;
    }

    public ReflectField(Class<?> cls, String name) {
        this.mName = name;
        this.mClass = cls;
    }

    public ReflectField(Object object, String name) {
        this.mName = name;
        if (object != null) {
            if (object instanceof Class<?>) {
                this.mClass = (Class<?>) object;
            } else {
                this.mObject = object;
                this.mClass = object.getClass();
            }
        }
    }


    public void setStaticType(int staticValue) {
        this.staticType = staticValue;
    }

    public void set(Object value) throws ReflectException {
        synchronized (mFiledLock) {
            prepareFiled();
            try {
                assessfield.set(mObject, value);
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }

    public Object get() throws ReflectException {
        synchronized (mFiledLock) {
            prepareFiled();
            try {
                return assessfield.get(mObject);
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }


    private void prepareFiled() throws ReflectException {
        synchronized (mFiledLock) {
            try {
                if (assessfield != null) {
                    return;
                }
                Field findFiled = mFiled;

                if (findFiled == null) {
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
                            findFiled = findField(relfectClass,filedName);
                        }catch (Throwable ignore){

                        }
                    }
                    if (findFiled == null){
                        Class<?> findClass = null;
                        if (mClass != null) {
                            findClass = mClass;
                        } else if (mClassName != null) {
                            findClass = Class.forName(mClassName);
                        }
                        findFiled = findField(findClass, filedName);
                    }
                }
                if (findFiled == null) {
                    throw new NoSuchFieldException("can not find filed: " + mName);
                }
                int modifiers = findFiled.getModifiers();
                if (!Modifier.isPublic(modifiers) ||
                        !Modifier.isPublic(findFiled
                                .getDeclaringClass()
                                .getModifiers())) {
                    if (!findFiled.isAccessible()) {
                        findFiled.setAccessible(true);
                    }
                }

                if (Modifier.isFinal(modifiers)) {
                    try {
                        Field modifiersField = Field.class.getDeclaredField("accessFlags");
                        if (!modifiersField.isAccessible()) {
                            modifiersField.setAccessible(true);
                        }
                        modifiersField.setInt(findFiled, modifiers & ~Modifier.FINAL);
                    } catch (Throwable ignore) {

                    }
                    try {
                        Field modifiersField = Field.class.getDeclaredField("modifiers");
                        if (!modifiersField.isAccessible()) {
                            modifiersField.setAccessible(true);
                        }
                        modifiersField.setInt(findFiled, modifiers & ~Modifier.FINAL);
                    } catch (Throwable ignore) {

                    }
                }
                assessfield = findFiled;
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }

        }
    }

    private Field findField(Class<?> findClass, String filedName) {
        for (Class<?> clazz = findClass; clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(filedName);
                boolean staticModifiers = Modifier.isStatic(field.getModifiers());
                if ((staticType == 0 && staticModifiers) || (staticType == 1 && !staticModifiers)) {
                    continue;
                }
                return field;
            } catch (Exception ignore) {
            }
        }
        return null;
    }
}
