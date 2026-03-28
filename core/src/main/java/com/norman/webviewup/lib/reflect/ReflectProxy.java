package com.norman.webviewup.lib.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

class ReflectProxy {

    private final Object mProxyLock = new Object();

    /** 精确匹配表：方法名 + 参数类型签名 → Invoke */
    private final Map<MethodSignature, Invoke> mInvokeMap = new HashMap<>();

    /** 模糊匹配表：仅方法名 → Invoke（注解 fuzzy=true 时注册） */
    private final Map<String, Invoke> mFuzzyInvokeMap = new HashMap<>();

    private Class<?>[] mAllInterfaces;

    private Class<?> mProxyClass;
    private Class<?> mClass;

    private String mClassName;


    private Object mTargetObject;


    public ReflectProxy(Class<?> clazz) {
        this.mClass = clazz;
    }

    public ReflectProxy(String className) {
        this.mClassName = className;
    }

    public void addInvoke(Invoke invoke) throws ReflectException {
       addInvoke(invoke, true, false);
    }

    public void addInvoke(Invoke invoke, boolean override) throws ReflectException {
        addInvoke(invoke, override, false);
    }

    /**
     * @param invoke   要注册的拦截器
     * @param override 是否覆盖已有的同签名精确条目
     * @param fuzzy    true → 只按方法名模糊路由，忽略参数类型
     */
    public void addInvoke(Invoke invoke, boolean override, boolean fuzzy) throws ReflectException {
        if (invoke == null) return;
        synchronized (mProxyLock) {
            prepareAllInterface();
            try {
                if (fuzzy) {
                    // 模糊：只以方法名为 key，覆盖策略同上
                    if (override || !mFuzzyInvokeMap.containsKey(invoke.signature.name)) {
                        mFuzzyInvokeMap.put(invoke.signature.name, invoke);
                    }
                } else {
                    if (!override && mInvokeMap.containsKey(invoke.signature)) {
                        return;
                    }
                    mInvokeMap.put(invoke.signature, invoke);
                }
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }
    }

    public void setTarget(Object target) {
        this.mTargetObject = target;
    }

    public Object newProxyInstance() throws ReflectException {
        synchronized (mProxyLock) {
            try {
                prepareAllInterface();
                return Proxy.newProxyInstance(
                        mProxyClass.getClassLoader(),
                        mAllInterfaces,
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxyObject, Method method, Object[] args) {
                                InvokeContext invokeContext = new InvokeContext(
                                        mTargetObject,
                                        proxyObject,
                                        mProxyClass,
                                        args,
                                        method);

                                // 1. 先精确匹配（方法名 + 参数类型）
                                Invoke invoke = mInvokeMap.get(invokeContext.signature);

                                // 2. 精确失败 → 模糊兜底（仅按方法名）
                                if (invoke == null) {
                                    invoke = mFuzzyInvokeMap.get(method.getName());
                                }

                                if (invoke != null) {
                                    invoke.onInvoke(invokeContext);
                                }
                                return invokeContext.replace ?
                                        invokeContext.replaceResult :
                                        invokeContext.invoke();
                            }
                        });
            } catch (Throwable throwable) {

                throw new ReflectException(throwable);
            }

        }

    }

    private void prepareAllInterface() throws ReflectException {
        synchronized (mProxyLock) {
            try {
                if (mAllInterfaces != null && mAllInterfaces.length !=0) {
                    return;
                }
                if (mProxyClass == null) {
                    mProxyClass = mClass;
                }
                if (mProxyClass == null) {
                    mProxyClass = Class.forName(mClassName);
                }
                List<Class<?>> interfaceList = new ArrayList<>();
                Queue<Class<?>> findClassQueue = new LinkedList<>();
                findClassQueue.add(mProxyClass);
                while (!findClassQueue.isEmpty()) {
                    Class<?> findClass = findClassQueue.remove();
                    if (findClass.isInterface()) {
                        if (!interfaceList.contains(findClass)) {
                            interfaceList.add(findClass);
                        }
                    }
                    Class<?> superClass = findClass != Object.class ?
                            findClass.getSuperclass() :
                            null;
                    if (superClass != null) {
                        findClassQueue.add(superClass);
                    }
                    Class<?>[] interfaces = findClass.getInterfaces();
                    findClassQueue.addAll(Arrays.asList(interfaces));
                }

                if (interfaceList.size() == 0) {
                    throw new IllegalArgumentException(mProxyClass + " not exist interfaces");
                }
                mAllInterfaces = new Class[interfaceList.size()];
                mAllInterfaces = interfaceList.toArray(mAllInterfaces);
            } catch (Throwable throwable) {
                throw new ReflectException(throwable);
            }
        }


    }

    public static abstract class Invoke {

        private final MethodSignature signature;

        public Invoke(String name, Class<?>... paramTypes) {
            signature = new MethodSignature(name, paramTypes);
        }

        protected abstract void onInvoke(InvokeContext invokeContext);



        public String getName() {
            return signature.name;
        }

        public Class<?>[] getParamTypes() {
            return signature.paramTypes;
        }

        MethodSignature getSignature() {
            return signature;
        }
    }

    static class MethodSignature {
        private final String name;

        private final Class<?>[] paramTypes;

        public MethodSignature(String name, Class<?>[] paramTypes) {
            this.name = name;
            this.paramTypes = paramTypes == null ? new Class<?>[0] : paramTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodSignature that = (MethodSignature) o;
            return Objects.equals(name, that.name) && Arrays.equals(paramTypes, that.paramTypes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name);
            result = 31 * result + Arrays.hashCode(paramTypes);
            return result;
        }
    }

    public static class InvokeContext {
        public final Object target;

        public final Object proxy;

        public final Class<?> proxyClass;

        public final Object[] args;

        private final Method method;

        private Object replaceResult;

        private Object invokeResult;
        private boolean invokeCalled;

        private boolean replace;

        private static final Method HASH_CODE;
        private static final Method EQUALS;
        private static final Method TO_STRING;

        private final MethodSignature signature;

        static {
            Class<Object> object = Object.class;
            try {
                HASH_CODE = object.getDeclaredMethod("hashCode");
                EQUALS = object.getDeclaredMethod("equals", object);
                TO_STRING = object.getDeclaredMethod("toString");
            } catch (NoSuchMethodException e) {
                // Never happens.
                throw new Error(e);
            }
        }

        public InvokeContext(Object target,
                             Object proxy,
                             Class<?> proxyClass,
                             Object[] args,
                             Method method) {
            this.target = target;
            this.proxy = proxy;
            this.proxyClass = proxyClass;
            this.args = args;
            this.method = method;
            this.signature = new MethodSignature(method.getName(), method.getParameterTypes());
        }

        public final void setResult(Object replaceResult) {
            this.replaceResult = replaceResult;
            this.replace = true;
        }

        public final Object invoke() throws ReflectException {
            try {
                if (invokeCalled) {
                    return invokeResult;
                }
                if (method.equals(HASH_CODE)) {
                    invokeResult = System.identityHashCode(proxy);
                } else if (method.equals(EQUALS)) {
                    invokeResult = proxy == args[0];
                } else if (method.equals(TO_STRING)) {
                    invokeResult = proxyClass.getName();
                } else if (target != null) {
                    invokeResult = method.invoke(target, args);
                } else {
                    Class<?> returnType = method.getReturnType();
                    if (returnType.isPrimitive()) {
                        if (boolean.class == returnType) {
                           invokeResult = false;
                        } else if (int.class == returnType) {
                            invokeResult = 0;
                        } else if (long.class == returnType) {
                            invokeResult = 0;
                        } else if (short.class == returnType) {
                            invokeResult = 0;
                        } else if (byte.class == returnType) {
                            invokeResult =0;
                        } else if (double.class == returnType) {
                            invokeResult =0.0d;
                        } else if (float.class == returnType) {
                            invokeResult =0.0f;
                        } else if (char.class == returnType) {
                            invokeResult = '\u0000';
                        } else  {
                            invokeResult = null;
                        }
                    } else {
                        invokeResult = null;
                    }
                }
                invokeCalled = true;
                return invokeResult;
            } catch (Throwable e) {
                throw new ReflectException(e);
            }
        }

        public final String getName() {
            return method.getName();
        }

        public final Class<?>[] getParameterTypes() {
            return method.getParameterTypes();
        }

        public final Annotation[][] getParameterAnnotations() {
            return method.getParameterAnnotations();
        }
    }

}
