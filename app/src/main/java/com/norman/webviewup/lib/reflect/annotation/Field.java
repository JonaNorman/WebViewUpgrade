package com.norman.webviewup.lib.reflect.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target({METHOD})
public @interface Field {
    int STATIC = 1;
    int OBJECT = 0;
    int ANY = -1;

    String value() default "";

    int type() default OBJECT;
}
