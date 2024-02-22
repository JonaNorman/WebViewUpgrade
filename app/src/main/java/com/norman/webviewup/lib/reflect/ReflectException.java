package com.norman.webviewup.lib.reflect;


import java.lang.reflect.InvocationTargetException;

public class ReflectException extends RuntimeException {


    public ReflectException(String message, Throwable cause) {
        super(message, getCause(cause));
        setStackTrace(cause.getStackTrace());
    }

    public ReflectException(Throwable cause) {
        super(getCause(cause));
        setStackTrace(cause.getStackTrace());
    }

    public ReflectException(String message) {
        super(message);
    }

    private static Throwable getCause(Throwable throwable) {
        if (throwable instanceof InvocationTargetException) {
            InvocationTargetException invocationTargetException = (InvocationTargetException) throwable;
            Throwable cause = invocationTargetException.getCause();
            if ( cause!= null) {
                return cause;
            } else {
                return invocationTargetException;
            }
        }
        return throwable instanceof ReflectException ? throwable.getCause() : throwable;
    }
}
