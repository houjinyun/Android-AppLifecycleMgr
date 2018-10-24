package com.hm.iou.lifecycle.plugin

import java.util.jar.JarEntry
import java.util.jar.JarFile

class ScanUtil {

    static final PROXY_CLASS_PREFIX = "Heima\$\$"
    static final PROXY_CLASS_SUFFIX = "\$\$Proxy.class"
    static final PROXY_CLASS_PACKAGE_NAME = "com/hm/iou/lifecycle/apt/proxy"

    static final REGISTER_CLASS_FILE_NAME = "com/hm/lifecycle/api/AppLifeCycleManager.class"

    //包含生命周期管理初始化类的文件，及包含 com.hm.lifecycle.api.AppLifeCycleManager 类的class文件或者jar文件
    static File FILE_CONTAINS_INIT_CLASS

    /**
     * 判断该class是否是我们的目标类
     *
     * @param file
     * @return
     */
    static boolean isTargetProxyClass(File file) {
        if (file.name.endsWith(PROXY_CLASS_SUFFIX) && file.name.startsWith(PROXY_CLASS_PREFIX)) {
            return true
        }
        return false
    }

    /**
     * 扫描jar包里的所有class文件：
     * 1.通过包名识别所有需要注入的类名
     * 2.找到注入管理类所在的jar包，后面我们会在该jar包里进行代码注入
     *
     * @param jarFile
     * @param destFile
     * @return
     */
    static List<String> scanJar(File jarFile, File destFile) {
        def file = new JarFile(jarFile)
        Enumeration<JarEntry> enumeration = file.entries()
        List<String> list = null
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement()
            String entryName = jarEntry.getName()
            if (entryName == REGISTER_CLASS_FILE_NAME) {
                //标记这个jar包包含 AppLifeCycleManager.class
                //扫描结束后，我们会生成注册代码到这个文件里
                FILE_CONTAINS_INIT_CLASS = destFile
            } else {
                if (entryName.startsWith(PROXY_CLASS_PACKAGE_NAME)) {
                    if (list == null) {
                        list = new ArrayList<>()
                    }
                    list.addAll(entryName.substring(entryName.lastIndexOf("/") + 1))
                }
            }
        }
        return list
    }

    static boolean shouldProcessPreDexJar(String path) {
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }

}