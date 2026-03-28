package com.norman.webviewup.lib.reflect.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({METHOD})
@Inherited
public @interface Method {
    int STATIC = 1;
    int OBJECT = 0;
    int ANY = -1;

    String value() default "";

    int type() default OBJECT;

    /**
     * 模糊匹配模式。
     *
     * 设为 true 时，系统只按方法名匹配，忽略参数列表（数量和类型均不限）。
     * 适用于在不同 Android 版本上参数签名经常变化的内部 API（如 IActivityManager.bindService 家族）。
     *
     * 设为 false（默认）时，按方法名 + 参数类型精确匹配，类型安全。
     */
    boolean fuzzy() default false;
}
