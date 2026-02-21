package com.zcore.zexreflection.utils;

import com.zcore.zexreflection.annotation.ZClass;
import com.zcore.zexreflection.annotation.ZClassName;
import com.zcore.zexreflection.annotation.ZClassNameNotProcess;

/**
 * Created by Milk on 2022/2/18.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ClassUtil {
    public static Class<?> classReady(Class<?> clazz) {
        ZClassNameNotProcess bClassNameNotProcess = clazz.getAnnotation(ZClassNameNotProcess.class);
        if (bClassNameNotProcess != null) {
            return classReady(bClassNameNotProcess.value());
        }
        ZClass annotation = clazz.getAnnotation(ZClass.class);
        if (annotation != null) {
            return annotation.value();
        }
        ZClassName bClassName = clazz.getAnnotation(ZClassName.class);
        if (bClassName != null) {
            return classReady(bClassName.value());
        }
        return null;
    }

    private static Class<?> classReady(String clazz) {
        try {
            return Class.forName(clazz);
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }
}
