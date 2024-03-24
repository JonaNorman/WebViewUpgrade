package com.norman.webviewup.lib.reflect;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.norman.webviewup.lib.reflect.annotation.ClassName;
import com.norman.webviewup.lib.reflect.annotation.ClassType;
import com.norman.webviewup.lib.reflect.annotation.Constructor;
import com.norman.webviewup.lib.reflect.annotation.Field;
import com.norman.webviewup.lib.reflect.annotation.Method;
import com.norman.webviewup.lib.reflect.annotation.ParameterName;
import com.norman.webviewup.lib.reflect.annotation.ParameterType;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeAccess<T> {

    private final static Map<Class<?>, Object> STATIC_ACCESS_MAP = new ConcurrentHashMap<>();

    private volatile Object assessObject;

    private final Class<T> buildClass;

    private T proxy;


    private boolean staticAccess;

    public RuntimeAccess(Class<T> buildClass) {
        this.buildClass = buildClass;
    }

    public RuntimeAccess(Class<T> buildClass, Object obj) {
        this.buildClass = buildClass;
        this.assessObject = obj;
    }

    public void setAssessObject(Object assessObject) {
        this.assessObject = assessObject;
    }


    public static <T> T staticAccess(Class<T> buildClass) {
        Object access = STATIC_ACCESS_MAP.get(buildClass);
        if (access != null) {
            return (T) access;
        }
        RuntimeAccess<T> runtimeAccess = new RuntimeAccess<>(buildClass);
        access = runtimeAccess.get();
        STATIC_ACCESS_MAP.put(buildClass, access);
        return (T) access;
    }

    public static <T> T objectAccess(Class<T> buildClass, Object object) {
        RuntimeAccess<T> runtimeAccess = new RuntimeAccess<>(buildClass, object);
        return runtimeAccess.get();
    }

    boolean isStaticAccess() {
        return staticAccess;
    }

    public T get() throws ReflectException {
        try {
            if (proxy != null) {
                return proxy;
            }
            if (!buildClass.isInterface()) {
                throw new IllegalArgumentException("build class must be interface");
            }
            ClassType classTypeAnnotation = buildClass.getAnnotation(ClassType.class);
            ClassName classNameAnnotation = buildClass.getAnnotation(ClassName.class);
            Class<?> annotationClass = null;
            if (classNameAnnotation != null) {
                annotationClass = Class.forName(classNameAnnotation.value());
            } else if (classTypeAnnotation != null) {
                annotationClass = classTypeAnnotation.value();
            }
            final Class<?> reflectClass = annotationClass;
            if (reflectClass == null) {
                throw new NullPointerException(buildClass + " must use " + ClassType.class + " or" + ClassName.class);
            }
            ReflectProxy reflectProxy = new ReflectProxy(buildClass);
            boolean allStaticAnnotation = true;
            for (java.lang.reflect.Method method : buildClass.getMethods()) {
                Method methodAnnotation = method.getAnnotation(Method.class);
                Constructor constructorAnnotation = method.getAnnotation(Constructor.class);
                Field filedAnnotation = method.getAnnotation(Field.class);
                ReflectProxy.Invoke invoke = null;
                if (methodAnnotation != null) {
                    if (methodAnnotation.type() != Method.STATIC) {
                        allStaticAnnotation = false;
                    }
                    invoke = new ReflectProxy.Invoke(method.getName(), method.getParameterTypes()) {
                        @Override
                        protected void onInvoke(ReflectProxy.InvokeContext invokeContext) {
                            Class<?>[] methodParameterTypes = findParameterTypes(invokeContext);
                            String methodName = methodAnnotation.value();
                            if (TextUtils.isEmpty(methodName)) {
                                methodName = invokeContext.getName();
                            }
                            Object assessObj = checkAssessObject(reflectClass);
                            ReflectMethod reflectMethod = new ReflectMethod(reflectClass, methodName, methodParameterTypes);
                            if (assessObj != null) {
                                reflectMethod = new ReflectMethod(assessObj, methodName, methodParameterTypes);
                            }
                            reflectMethod.setStaticType(methodAnnotation.type());
                            Object result = reflectMethod.invoke(invokeContext.args);
                            invokeContext.setResult(result);
                        }
                    };
                } else if (constructorAnnotation != null) {
                    invoke = new ReflectProxy.Invoke(method.getName(), method.getParameterTypes()) {
                        @Override
                        protected void onInvoke(ReflectProxy.InvokeContext invokeContext) {
                            Class<?>[] constructorParameterTypes = findParameterTypes(invokeContext);
                            ReflectConstructor reflectMethod = new ReflectConstructor(reflectClass, constructorParameterTypes);
                            Object result = reflectMethod.newInstance(invokeContext.args);
                            invokeContext.setResult(result);
                        }
                    };
                } else if (filedAnnotation != null) {
                    if (filedAnnotation.type() != Field.STATIC) {
                        allStaticAnnotation = false;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == Void.TYPE && method.getParameterTypes().length == 0) {
                        throw new IllegalArgumentException("method of set filed  must set parameter");
                    }
                    invoke = new ReflectProxy.Invoke(method.getName(), method.getParameterTypes()) {
                        @Override
                        protected void onInvoke(ReflectProxy.InvokeContext invokeContext) {
                            String filedName = filedAnnotation.value();
                            if (TextUtils.isEmpty(filedName)) {
                                filedName = invokeContext.getName();
                            }
                            Object assessObj = checkAssessObject(reflectClass);
                            ReflectField reflectField = new ReflectField(reflectClass, filedName);
                            if (assessObj != null) {
                                reflectField = new ReflectField(assessObj, filedName);
                            }
                            reflectField.setStaticType(filedAnnotation.type());
                            Class<?> returnType = method.getReturnType();
                            if (returnType == Void.TYPE) {
                                reflectField.set(invokeContext.args[0]);
                                invokeContext.setResult(null);
                            } else {
                                Object kkk = reflectField.get();
                                invokeContext.setResult(kkk);
                            }
                        }
                    };
                }
                if (invoke != null) {
                    reflectProxy.addInvoke(invoke);
                }
            }
            proxy = (T) reflectProxy.newProxyInstance();
            staticAccess = allStaticAnnotation;
            return proxy;
        } catch (
                Throwable throwable) {
            throw new ReflectException(throwable);
        }
    }

    private Object checkAssessObject(Class<?> reflectCls) {
        Object obj = assessObject;
        if (obj != null && !Objects.equals(obj.getClass(), reflectCls)) {
            throw new IllegalArgumentException(reflectCls + " is not same as " + assessObject.getClass());
        }
        return obj;
    }

    @NonNull
    private static Class<?>[] findParameterTypes(ReflectProxy.InvokeContext invokeContext) {
        Annotation[][] invokeAnnotations = invokeContext.getParameterAnnotations();
        Class<?>[] invokeTypes = invokeContext.getParameterTypes();
        Class<?>[] methodParameterTypes = new Class<?>[invokeTypes.length];
        for (int i = 0; i < methodParameterTypes.length; i++) {
            Annotation[] annotations = invokeAnnotations[i];
            ParameterName parameterNameAnnotation = null;
            ParameterType parameterTypeAnnotation = null;
            if (annotations != null) {
                for (Annotation annotation : annotations) {
                    if (annotation instanceof ParameterName) {
                        parameterNameAnnotation = (ParameterName) annotation;
                        break;
                    } else if (annotation instanceof ParameterType) {
                        parameterTypeAnnotation = (ParameterType) annotation;
                        break;
                    }
                }
            }
            Class<?> parameterClass = null;
            if (parameterNameAnnotation != null) {
                String className = parameterNameAnnotation.value();
                if (!TextUtils.isEmpty(className)) {
                    try {
                        parameterClass = Class.forName(className);
                    } catch (Throwable throwable) {
                        throw new ReflectException(throwable);
                    }
                }
            } else if (parameterTypeAnnotation != null) {
                parameterClass = parameterTypeAnnotation.value();
            }
            if (parameterClass == null) {
                parameterClass = invokeTypes[i];
            }
            methodParameterTypes[i] = parameterClass;
        }
        return methodParameterTypes;
    }


}
