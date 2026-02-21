package com.zcore.zexreflection;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.zcore.zexreflection.annotation.ZClass;
import com.zcore.zexreflection.annotation.ZClassName;
import com.zcore.zexreflection.annotation.ZClassNameNotProcess;
import com.zcore.zexreflection.annotation.ZConstructor;
import com.zcore.zexreflection.annotation.ZConstructorNotProcess;
import com.zcore.zexreflection.annotation.ZField;
import com.zcore.zexreflection.annotation.ZFieldCheckNotProcess;
import com.zcore.zexreflection.annotation.ZFieldNotProcess;
import com.zcore.zexreflection.annotation.ZFieldSetNotProcess;
import com.zcore.zexreflection.annotation.ZMethodCheckNotProcess;
import com.zcore.zexreflection.annotation.ZParamClass;
import com.zcore.zexreflection.annotation.ZParamClassName;
import com.zcore.zexreflection.utils.Reflector;

/**
 * Created by Milk on 2022/2/15.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@SuppressWarnings("unchecked")
public class ZexReflection {
    public static boolean DEBUG = false;
    public static boolean CACHE = false;
    private static final Map<Class<?>, Object> sProxyCache = new HashMap<>();
    private static final Map<Class<?>, Object> sProxyWithExceptionCache = new HashMap<>();

    // key caller
    private static final WeakHashMap<Object, Map<Class<?>, Object>> sCallerProxyCache = new WeakHashMap<>();

//    public static <T> T create(Class<T> clazz, final Object caller) {
//        return create(clazz, caller, false);
//    }

    public static <T> T create(Class<T> clazz, final Object caller, boolean withException) {
        try {
            T proxy = getProxy(clazz, caller, withException);
            if (proxy != null)
                return proxy;
            final WeakReference<Object> weakCaller = caller == null ? null : new WeakReference<>(caller);

            final Class<?> aClass = getClassNameByBlackClass(clazz);
            Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    Class<?> returnType = method.getReturnType();

                    try {
                        boolean isStatic = weakCaller == null;

                        Object callerByWeak = isStatic ? null : weakCaller.get();

                        // fidel
                        ZField bField = method.getAnnotation(ZField.class);
                        ZFieldNotProcess bFieldNotProcess = method.getAnnotation(ZFieldNotProcess.class);
                        if (bField != null || bFieldNotProcess != null) {
                            Object call;
                            Reflector on = Reflector.on(aClass).field(name);
                            if (isStatic) {
                                call = on.get();
                            } else {
                                if (callerByWeak == null) {
                                    return generateNullValue(returnType);
                                }
                                call = on.get(callerByWeak);
                            }
                            return call;
                        }

                        // void
                        ZFieldSetNotProcess bFieldSetNotProcess = method.getAnnotation(ZFieldSetNotProcess.class);
                        if (bFieldSetNotProcess != null) {
                            // startsWith "_set_"
                            name = name.substring("_set_".length());
                            Reflector on = Reflector.on(aClass).field(name);
                            if (isStatic) {
                                on.set(args[0]);
                            } else {
                                if (callerByWeak == null) {
                                    return 0;
                                }
                                on.set(callerByWeak, args[0]);
                            }
                            return 0;
                        }

                        // check field
                        ZFieldCheckNotProcess bFieldCheckNotProcess = method.getAnnotation(ZFieldCheckNotProcess.class);
                        if (bFieldCheckNotProcess != null) {
                            // startsWith "_check_"
                            name = name.substring("_check_".length());
                            try {
                                Reflector on = Reflector.on(aClass).field(name);
                                return on.getField();
                            } catch (Throwable ignored) {
                                return null;
                            }
                        }

                        Class<?>[] paramClass = getParamClass(method);

                        // check method
                        ZMethodCheckNotProcess bMethodCheckNotProcess = method.getAnnotation(ZMethodCheckNotProcess.class);
                        if (bMethodCheckNotProcess != null) {
                            // startsWith "_check_"
                            name = name.substring("_check_".length());
                            try {
                                return Reflector.on(aClass).method(name, paramClass).getMethod();
                            } catch (Throwable ignored) {
                                return null;
                            }
                        }

                        // method
                        ZConstructor bConstructor = method.getAnnotation(ZConstructor.class);
                        ZConstructorNotProcess bConstructorNotProcess = method.getAnnotation(ZConstructorNotProcess.class);
                        if (bConstructor != null || bConstructorNotProcess != null) {
                            Reflector on = Reflector.on(aClass).constructor(paramClass);
                            return on.newInstance(args);
                        }

                        Object call;
                        Reflector on = Reflector.on(aClass).method(name, paramClass);
                        if (isStatic) {
                            call = on.call(args);
                        } else {
                            if (callerByWeak == null) {
                                return generateNullValue(returnType);
                            }
                            call = on.callByCaller(callerByWeak, args);
                        }
                        return call;
                    } catch (Throwable throwable) {
                        if (DEBUG) {
                            if (throwable.getCause() != null) {
                                throwable.getCause().printStackTrace();
                            } else {
                                throwable.printStackTrace();
                            }
                        }
                        if (throwable instanceof BlackNullPointerException) {
                            throw new NullPointerException(throwable.getMessage());
                        }
                        if (withException) {
                            throw throwable;
                        }
                    }

                    return generateNullValue(returnType);
                }
            });

            if (caller == null) {
                if (withException) {
                    sProxyWithExceptionCache.put(clazz, o);
                } else {
                    sProxyCache.put(clazz, o);
                }
            }
            return (T) o;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static <T> T getProxy(Class<T> clazz, final Object caller, boolean withException) {
        try {
            if (caller == null) {
                Object o;
                if (withException) {
                    o = sProxyWithExceptionCache.get(clazz);
                } else {
                    o = sProxyCache.get(clazz);
                }
                if (o != null) {
                    return (T) o;
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static Class<?>[] getParamClass(Method method) throws Throwable {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<?>[] param = new Class[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Annotation[] parameterAnnotation = parameterAnnotations[i];
            boolean found = false;
            for (Annotation annotation : parameterAnnotation) {
                if (annotation instanceof ZParamClassName) {
                    found = true;
                    param[i] = Class.forName(((ZParamClassName) annotation).value());
                    break;
                } else if (annotation instanceof ZParamClass) {
                    found = true;
                    param[i] = ((ZParamClass) annotation).value();
                    break;
                }
            }

            if (!found) {
                param[i] = parameterTypes[i];
            }
        }
        return param;
    }

    private static Class<?> getClassNameByBlackClass(Class<?> clazz) throws ClassNotFoundException {
        ZClass bClass = clazz.getAnnotation(ZClass.class);
        ZClassName bClassName = clazz.getAnnotation(ZClassName.class);
        ZClassNameNotProcess bClassNameNotProcess = clazz.getAnnotation(ZClassNameNotProcess.class);
        if (bClass == null && bClassName == null && bClassNameNotProcess == null) {
            throw new RuntimeException("Not found @BlackClass or @BlackStrClass");
        }

        if (bClass != null) {
            return bClass.value();
        } else if (bClassName != null) {
            return Class.forName(bClassName.value());
        } else {
            return Class.forName(bClassNameNotProcess.value());
        }
    }

    private static Object generateNullValue(Class<?> returnType) {
        if (returnType == void.class) {
            return 0;
        }
        if (returnType.isPrimitive()) {
            throw new BlackNullPointerException("value is null!");
        }
        return null;
    }
}
