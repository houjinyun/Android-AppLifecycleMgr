package com.hm.iou.lifecycle.annotation;

/**
 * Created by hjy on 2018/10/23.
 */

public class LifeCycleConfig {

    /**
     * 生成代理类的包名
     */
    public static final String PROXY_CLASS_PACKAGE_NAME = "com.hm.iou.lifecycle.apt.proxy";

    /**
     * 生成代理类统一的后缀
     */
    public static final String PROXY_CLASS_SUFFIX = "$$Proxy";

    /**
     * 生成代理类统一的前缀
     */
    public static final String PROXY_CLASS_PREFIX = "Heima$$";

}
