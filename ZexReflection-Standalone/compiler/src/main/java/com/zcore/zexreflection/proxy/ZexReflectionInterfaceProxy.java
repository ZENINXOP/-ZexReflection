package com.zcore.zexreflection.proxy;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import com.zcore.zexreflection.ZexReflectionInterfaceInfo;
import com.zcore.zexreflection.annotation.ZConstructor;
import com.zcore.zexreflection.annotation.ZConstructorNotProcess;
import com.zcore.zexreflection.annotation.ZFieldCheckNotProcess;
import com.zcore.zexreflection.annotation.ZFieldNotProcess;
import com.zcore.zexreflection.annotation.ZFieldSetNotProcess;
import com.zcore.zexreflection.annotation.ZMethodCheckNotProcess;
import com.zcore.zexreflection.annotation.ZParamClass;
import com.zcore.zexreflection.annotation.ZClassNameNotProcess;
import com.zcore.zexreflection.annotation.ZParamClassName;

/**
 * Created by sunwanquan on 2020/1/8.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
public class ZexReflectionInterfaceProxy {

    private final List<ZexReflectionInterfaceInfo> mReflections = new ArrayList<>();
    // fake.android.app.ActivityThreadStatic or ActivityThreadContext
    private final String mClassName;
    // fake.android.app
    private final String mPackageName;
    // fake.android.app.ActivityThread
    private final String mOrigClassName;
    private Map<String, String> realMaps;

    public ZexReflectionInterfaceProxy(String packageName, String className, String origClassName) {
        mPackageName = packageName;
        mClassName = className;
        mOrigClassName = origClassName;
    }

    public JavaFile generateInterfaceCode() {
        String finalClass = mClassName
                .replace(mPackageName + ".", "")
                .replace(".", "");
        AnnotationSpec annotationSpec = AnnotationSpec.builder(ZClassNameNotProcess.class)
                .addMember("value","$S", realMaps.get(mOrigClassName))
                .build();

        // generaClass
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(finalClass)
                .addAnnotation(annotationSpec)
                .addModifiers(Modifier.PUBLIC);

        for (ZexReflectionInterfaceInfo reflection : mReflections) {
            MethodSpec.Builder method = MethodSpec.methodBuilder(reflection.getExecutableElement().getSimpleName().toString())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

            List<ParameterSpec> parameterSpecs = new ArrayList<>();
            for (VariableElement typeParameter : reflection.getExecutableElement().getParameters()) {
                ParameterSpec.Builder builder = ParameterSpec.builder(ClassName.get(typeParameter.asType()), typeParameter.getSimpleName().toString());
                if (typeParameter.getAnnotation(ZParamClassName.class) != null) {
                    ZParamClassName annotation = typeParameter.getAnnotation(ZParamClassName.class);
                    builder.addAnnotation(AnnotationSpec.get(annotation));
                }
                if (typeParameter.getAnnotation(ZParamClass.class) != null) {
                    ZParamClass annotation = typeParameter.getAnnotation(ZParamClass.class);
                    String annotationValue = getClass(annotation).toString();
                    Class<?> aClass = parseBaseClass(annotationValue);
                    if (aClass != null) {
                        builder.addAnnotation(AnnotationSpec.builder(ZParamClass.class)
                                .addMember("value", "$T.class", aClass).build());
                    } else {
                        builder.addAnnotation(AnnotationSpec.builder(ZParamClass.class)
                                .addMember("value", annotationValue + ".class").build());
                    }
                }

                ParameterSpec build = builder.build();
                parameterSpecs.add(build);
                method.addParameter(build);
            }
            TypeName typeName = TypeName.get(reflection.getExecutableElement().getReturnType());
            method.returns(typeName.box());
            if (reflection.isField()) {
                method.addAnnotation(AnnotationSpec.builder(ZFieldNotProcess.class).build());
                // set field
                interfaceBuilder.addMethod(generateFieldSet(reflection));
                // check field
                interfaceBuilder.addMethod(generateFieldCheck(reflection));
            } else {
                ZConstructor annotation = reflection.getExecutableElement().getAnnotation(ZConstructor.class);
                if (annotation != null) {
                    method.addAnnotation(AnnotationSpec.builder(ZConstructorNotProcess.class).build());
                } else {
                    // check method
                    interfaceBuilder.addMethod(generateMethodCheck(reflection, parameterSpecs));
                }
            }
            interfaceBuilder.addMethod(method.build());
        }
        return JavaFile.builder(mPackageName, interfaceBuilder.build()).build();
    }

    private MethodSpec generateFieldSet(ZexReflectionInterfaceInfo reflection) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("_set_" + reflection.getExecutableElement().getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(ClassName.get("java.lang", "Object"), "value", Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(ZFieldSetNotProcess.class).build());
        return method.build();
    }

    private MethodSpec generateFieldCheck(ZexReflectionInterfaceInfo reflection) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("_check_" + reflection.getExecutableElement().getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(AnnotationSpec.builder(ZFieldCheckNotProcess.class).build())
                .returns(Field.class);
        return method.build();
    }

    private MethodSpec generateMethodCheck(ZexReflectionInterfaceInfo reflection, List<ParameterSpec> parameterSpecs) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("_check_" + reflection.getExecutableElement().getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(AnnotationSpec.builder(ZMethodCheckNotProcess.class).build())
                .returns(Method.class);
        for (ParameterSpec parameterSpec : parameterSpecs) {
            method.addParameter(parameterSpec);
        }
        return method.build();
    }

    public void add(ZexReflectionInterfaceInfo interfaceInfo) {
        mReflections.add(interfaceInfo);
    }

    public void setRealMap(Map<String, String> realMaps) {
        this.realMaps = realMaps;
    }

    private static TypeMirror getClass(ZParamClass annotation) {
        try {
            annotation.value(); // this should throw
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
        return null; // can this ever happen ??
    }

    private static Class<?> parseBaseClass(String className) {
        switch (className) {
            case "int":
                return int.class;
            case "byte":
                return byte.class;
            case "short":
                return short.class;
            case "long":
                return long.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "boolean":
                return boolean.class;
            case "char":
                return char.class;
            case "int[]":
                return int[].class;
            case "byte[]":
                return byte[].class;
            case "short[]":
                return short[].class;
            case "long[]":
                return long[].class;
            case "float[]":
                return float[].class;
            case "double[]":
                return double[].class;
            case "boolean[]":
                return boolean[].class;
            case "char[]":
                return char[].class;
        }
        return null;
    }
}
