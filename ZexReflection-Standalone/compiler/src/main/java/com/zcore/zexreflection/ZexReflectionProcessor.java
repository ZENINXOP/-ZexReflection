package com.zcore.zexreflection;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import com.zcore.zexreflection.annotation.ZClass;
import com.zcore.zexreflection.annotation.ZConstructor;
import com.zcore.zexreflection.annotation.ZField;
import com.zcore.zexreflection.annotation.ZMethod;
import com.zcore.zexreflection.annotation.ZStaticField;
import com.zcore.zexreflection.annotation.ZStaticMethod;
import com.zcore.zexreflection.annotation.ZClassName;
import com.zcore.zexreflection.proxy.ZexReflectionInterfaceProxy;
import com.zcore.zexreflection.proxy.ZexReflectionProxy;

/**
 * Created by Milk on 2022/2/15.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * 此处无Bug
 */
@AutoService(Processor.class)
public class ZexReflectionProcessor extends AbstractProcessor {

    private Map<String, ZexReflectionProxy> mZexReflectionProxies;
    private Map<String, ZexReflectionInterfaceProxy> mZexReflectionInterfaceProxies;
    private Map<String, String> mRealMaps = new HashMap<>();

    private Messager mMessager;
    private Elements mElementUtils; //元素相关的辅助类
    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mMessager = processingEnv.getMessager();
        mElementUtils = processingEnv.getElementUtils();
        mFiler = processingEnv.getFiler();
        mZexReflectionProxies = new Hashtable<>();
        mZexReflectionInterfaceProxies = new Hashtable<>();
        mRealMaps = new Hashtable<>();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(ZClass.class.getCanonicalName());
        supportTypes.add(ZClassName.class.getCanonicalName());

        supportTypes.add(ZField.class.getCanonicalName());
        supportTypes.add(ZStaticField.class.getCanonicalName());

        supportTypes.add(ZMethod.class.getCanonicalName());
        supportTypes.add(ZStaticMethod.class.getCanonicalName());

        supportTypes.add(ZConstructor.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        mZexReflectionProxies.clear();
        mZexReflectionInterfaceProxies.clear();
        mRealMaps.clear();

        for (Element element : roundEnv.getElementsAnnotatedWith(ZClassName.class)) {
            ZClassName annotation = element.getAnnotation(ZClassName.class);
            doProcess(element, annotation.value());
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(ZClass.class)) {
            ZClass annotation = element.getAnnotation(ZClass.class);
            String aClass = getClass(annotation).toString();
            doProcess(element, aClass);
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(ZStaticMethod.class)) {
            doInterfaceProcess(element, true, false);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ZMethod.class)) {
            doInterfaceProcess(element, false, false);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ZStaticField.class)) {
            doInterfaceProcess(element, true, true);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ZField.class)) {
            doInterfaceProcess(element, false, true);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ZConstructor.class)) {
            doInterfaceProcess(element, true, false);
        }

        for (ZexReflectionInterfaceProxy value : mZexReflectionInterfaceProxies.values()) {
            try {
                value.setRealMap(mRealMaps);
                value.generateInterfaceCode().writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (ZexReflectionProxy value : mZexReflectionProxies.values()) {
            try {
                value.generateJavaCode().writeTo(mFiler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private void doInterfaceProcess(Element element, boolean isStatic, boolean isField) {
        String className = element.getEnclosingElement().asType().toString();
        ExecutableElement executableElement = (ExecutableElement) element;
        String packageName = mElementUtils.getPackageOf(executableElement).getQualifiedName().toString();

        ZexReflectionInterfaceInfo interfaceInfo = new ZexReflectionInterfaceInfo();
        interfaceInfo.setExecutableElement(executableElement);
        interfaceInfo.setField(isField);

        ZexReflectionInterfaceProxy reflectionInterfaceProxy = getReflectionInterfaceProxy(packageName,
                className + (isStatic ? "Static" : "Context"),
                className);
        reflectionInterfaceProxy.add(interfaceInfo);
    }

    private void doProcess(Element element, String realClassName) {
        String packageName = mElementUtils.getPackageOf(element).getQualifiedName().toString();
        String className = element.asType().toString();
        ZexReflectionInfo info = new ZexReflectionInfo();
        info.setRealClass(realClassName);
        info.setClassName(className);

        getReflectionProxy(packageName, className, info);

        // 创建两个基本类
        getReflectionInterfaceProxy(packageName, className + "Context",
                className);
        getReflectionInterfaceProxy(packageName, className + "Static",
                className);
        mRealMaps.put(className, realClassName);
    }

    private static TypeMirror getClass(ZClass annotation) {
        try {
            annotation.value(); // this should throw
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
        return null; // can this ever happen ??
    }

    public ZexReflectionProxy getReflectionProxy(String packageName, String className, ZexReflectionInfo info) {
        ZexReflectionProxy blackReflectionProxy = mZexReflectionProxies.get(className);
        if (blackReflectionProxy == null) {
            blackReflectionProxy = new ZexReflectionProxy(packageName, info);
            mZexReflectionProxies.put(className, blackReflectionProxy);
        }
        return blackReflectionProxy;
    }

    public ZexReflectionInterfaceProxy getReflectionInterfaceProxy(String packageName, String className, String origClassName) {
        ZexReflectionInterfaceProxy blackReflectionProxy = mZexReflectionInterfaceProxies.get(className);
        if (blackReflectionProxy == null) {
            blackReflectionProxy = new ZexReflectionInterfaceProxy(packageName, className, origClassName);
            mZexReflectionInterfaceProxies.put(className, blackReflectionProxy);
        }
        return blackReflectionProxy;
    }
}
