package com.hm.iou.lifecycle.apt;

import com.hm.iou.lifecycle.annotation.LifeCycleConfig;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Created by hjy on 2018/10/23.
 */

public class AppLikeProxyClassCreator {

    private Elements mElementUtils;
    private TypeElement mTypeElement;
    private String mProxyClassSimpleName;

    public AppLikeProxyClassCreator(Elements elements, TypeElement typeElement) {
        mElementUtils = elements;
        mTypeElement = typeElement;
        mProxyClassSimpleName = LifeCycleConfig.PROXY_CLASS_PREFIX +
                mTypeElement.getSimpleName().toString() +
                LifeCycleConfig.PROXY_CLASS_SUFFIX;
    }

    /**
     * 获取要生成的代理类的完整类名
     *
     * @return
     */
    public String getProxyClassFullName() {
        String name = LifeCycleConfig.PROXY_CLASS_PACKAGE_NAME + "."+ mProxyClassSimpleName;
        return name;
    }

    /**
     * 生成java代码
     */
    public String generateJavaCode() {
        StringBuilder sb = new StringBuilder();
        //设置包名
        sb.append("package ").append(LifeCycleConfig.PROXY_CLASS_PACKAGE_NAME).append(";\n\n");

        //设置import部分
        sb.append("import android.content.Context;\n");
        sb.append("import com.hm.lifecycle.api.IAppLike;\n");
        sb.append("import ").append(mTypeElement.getQualifiedName()).append(";\n\n");

        sb.append("public class ").append(mProxyClassSimpleName)
                .append(" implements ").append("IAppLike ").append(" {\n\n");

        //设置变量
        sb.append("  private ").append(mTypeElement.getSimpleName().toString()).append(" mAppLike;\n\n");

        //构造函数
        sb.append("  public ").append(mProxyClassSimpleName).append("() {\n");
        sb.append("  mAppLike = new ").append(mTypeElement.getSimpleName().toString()).append("();\n");
        sb.append("  }\n\n");

        //onCreate()方法
        sb.append("  public void onCreate(Context context) {\n");
        sb.append("    mAppLike.onCreate(context);\n");
        sb.append("  }\n\n");

        //getPriority()方法
        sb.append("  public int getPriority() {\n");
        sb.append("    return mAppLike.getPriority();\n");
        sb.append("  }\n\n");

        //onTerminate方法
        sb.append("  public void onTerminate() {\n");
        sb.append("    mAppLike.onTerminate();\n");
        sb.append("  }\n\n");


        sb.append("\n}");
        return sb.toString();
    }

}
