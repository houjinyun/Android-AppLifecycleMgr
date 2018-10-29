### 1. 前言

前面有一章讲过组件生命周期管理，参见[Android组件化开发实践（五）：组件生命周期管理](https://www.jianshu.com/p/65433846d38a)。之前只是为了讲解组件生命周期的概念，以及这样做的原因，但是这样实施过程中，会发现在壳工程里会出现很多硬编码，如果你引入的一个组件里有实现BaseAppLike的类，那么你就得在壳工程的Application.onCreate()方法里手动实例化该类，如果你删除一个类似的组件，同样你也得删除与之相应的代码。这显然是不灵活的，因为这要求壳工程的维护者必须知道，该工程引入的组件里有多少类是实现了BaseAppLike的，如果忘记一个或若干个，应用就可能出现问题。所以我们现在的目标就是，怎么去自动识别所有组件的BaseAppLike类，增加或删除组件时，不用修改任何代码。

### 2. 实现的思路

那么应用运行时怎么去识别所有实现了BaseAppLike的类，先讲讲我自己的思路，思路理清了之后我们再一步步去技术实现。

__初步思路：__
1. 定义一个注解来标识实现了BaseAppLike的类。
2. 通过APT技术，在组件编译时扫描和处理前面定义的注解，生成一个BaseAppLike的代理类，姑且称之为BaseAppLikeProxy，所有的代理类都在同一个包名下，这个包名下必须只包含代理类，且类名由固定的规则来生成，保证不同组件生成的代理类的类名不会重复。
3. 需要有一个组件生命周期管理类，初始化时能扫描到固定包名下有多少个类文件，以及类文件的名称，这个固定包名就是前面我们生成的BaseAppLikeProxy的包名，代理类都放在同一个包名下，是为了通过包名找到我们所有的目标类。
4. 组件集成后在应用的Application.onCreate()方法里，调用组件生命周期管理类的初始化方法。
5. 组件生命周期管理类的内部，扫描到所有的BaseAppLikeProxy类名之后，通过反射进行类实例化。

__初步技术难点：__
1. 需要了解APT技术，怎么在编译时动态生成java代码；
2. 应用在运行时，怎么能扫描到某个包名下有多少个class，以及他们的名称呢；

__更进一步的思考：__
前面的思路里，应用在运行时，可能需要扫描所有的class，然后通过class文件的包名来判断是不是我们的目标类，但是我们的要用到的可能只有几个，这显然效率是不高的。能不能在运行时，不扫描所有class文件，就已经知道了所有的BaseAppLikeProxy类名呢？首先想到的就是采用gradle插件技术，在应用打包编译时，动态插入字节码来实现。这里又会碰到几个技术难点：
1. 怎么制作gradle插件。
2. 怎么在打包时动态插入字节码。

### 3. 从0开始实现

接下来我们按照步骤来一步步实现，碰到问题就解决问题，看怎么来实现组件生命周期自动注册管理。这里面用到的技术会有：APT、groovy语言、gradle插件技术、ASM动态生成字节码，平时我们开发应用时一般不需要了解这些，所以会有一定的难度。

##### 3.1 注解定义
在Android Studio中，新建一个Java Library module，我命名为lifecycle-annotation，在该module中创建一个注解类，同时创建一个后面要生成代理类的相关配置，如下图所示：

![](https://upload-images.jianshu.io/upload_images/5955727-47abaa4614c2a9f4.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

相关代码如下：
```
//注解类，只能用来对类进行注解
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AppLifeCycle {
}

public class LifeCycleConfig {

    /**
     * 要生成的代理类的包名，该包名下不要有其他不相关的业务类
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
```

##### 3.2 重新定义IAppLike接口
新建一个Android Library module，命名为lifecycle-api，在这个module里定义IAppLike接口，以及一个生命周期管理类。

![](https://upload-images.jianshu.io/upload_images/5955727-856eb3e7b5561688.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

为了生成代理类，我们这里定义了一个接口IAppLike，组件只需实现该接口即可，同时定义了一个组件生命周期管理类AppLifeCycleManager，该类负责加载应用内所有实现了IAppLike的类。

```
public interface IAppLike {

    int MAX_PRIORITY = 10;
    int MIN_PRIORITY = 1;
    int NORM_PRIORITY = 5;

    int getPriority();
    void onCreate(Context context);
    void onTerminate();
}
```

再来看看生命周期管理类，逻辑很简单，通过一个List来存储所有的IAppLike，初始化时会根据优先级排序：
```
public class AppLifeCycleManager {

    private static List<IAppLike> APP_LIKE_LIST = new ArrayList<>();

    /**
     * 注册IAppLike类
     */
    public static void registerAppLike(IAppLike appLike) {
        APP_LIKE_LIST.add(appLike);
    }

    /**
     * 初始化，需要在Application.onCreate()里调用
     *
     * @param context
     */
    public static void init(Context context) {
        Collections.sort(APP_LIKE_LIST, new AppLikeComparator());
        for (IAppLike appLike : APP_LIKE_LIST) {
            appLike.onCreate(context);
        }
    }

    public static void terminate() {
        for (IAppLike appLike : APP_LIKE_LIST) {
            appLike.onTerminate();
        }
    }

    /**
     * 优先级比较器，优先级大的排在前面
     */
    static class AppLikeComparator implements Comparator<IAppLike> {

        @Override
        public int compare(IAppLike o1, IAppLike o2) {
            int p1 = o1.getPriority();
            int p2 = o2.getPriority();
            return p2 - p1;
        }
    }

}
```

##### 3.3 使用APT来生成IAppLike的代理类
新建一个Java Library module，命名为lifecycle-apt，在该module里实现我们自己的注解处理器。

在build.gradle里修改配置为：
```
apply plugin: 'java-library'

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    //这是谷歌提供的一个自动服务注册框架，需要用到
    implementation 'com.google.auto.service:auto-service:1.0-rc2'
    implementation project(':lifecycle-annotation')
}

sourceCompatibility = "1.7"
targetCompatibility = "1.7"
```
接下来就是实现我们自己的注解处理器了，工程结构如下图所示：

![](https://upload-images.jianshu.io/upload_images/5955727-1f99166e562b23b4.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

主要代码如下，代码逻辑加在注释里：
```
//核心的注解处理类，在这里我们可以扫描源代码里所有的注解，找到我们需要的注解，然后做出相应处理
@AutoService(Processor.class)
public class AppLikeProcessor extends AbstractProcessor {

    private Elements mElementUtils;
    private Map<String, AppLikeProxyClassCreator> mMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mElementUtils = processingEnvironment.getElementUtils();
    }

    /**
     * 返回该注解处理器要解析的注解
     *
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new LinkedHashSet<>();
        //返回注解类的全限定类名，我们这里要识别的注解类是 AppLifeCycle
        set.add(AppLifeCycle.class.getCanonicalName());
        return set;
    }

    //支持的源代码 java 版本号
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }

    //所有逻辑都在这里完成
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //这里返回所有使用了 AppLifeCycle 注解的元素
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(AppLifeCycle.class);
        mMap.clear();
        //遍历所有使用了该注解的元素
        for (Element element : elements) {
            //如果该注解不是用在类上面，直接抛出异常，该注解用在方法、字段等上面，我们是不支持的
            if (!element.getKind().isClass()) {
                throw new RuntimeException("Annotation AppLifeCycle can only be used in class.");
            }
            //强制转换为TypeElement，也就是类元素，可以获取使用该注解的类的相关信息
            TypeElement typeElement = (TypeElement) element;

            //这里检查一下，使用了该注解的类，同时必须要实现com.hm.lifecycle.api.IAppLike接口，否则会报错，因为我们要实现一个代理类
            List<? extends TypeMirror> mirrorList = typeElement.getInterfaces();
            if (mirrorList.isEmpty()) {
                throw new RuntimeException(typeElement.getQualifiedName() + " must implements interface com.hm.lifecycle.api.IAppLike");
            }
            boolean checkInterfaceFlag = false;
            for (TypeMirror mirror : mirrorList) {
                if ("com.hm.lifecycle.api.IAppLike".equals(mirror.toString())) {
                    checkInterfaceFlag = true;
                }
            }
            if (!checkInterfaceFlag) {
                throw new RuntimeException(typeElement.getQualifiedName() + " must implements interface com.hm.lifecycle.api.IAppLike");
            }

            //该类的全限定类名
            String fullClassName = typeElement.getQualifiedName().toString();
            if (!mMap.containsKey(fullClassName)) {
                System.out.println("process class name : " + fullClassName);
                //创建代理类生成器
                AppLikeProxyClassCreator creator = new AppLikeProxyClassCreator(mElementUtils, typeElement);
                mMap.put(fullClassName, creator);
            }
        }

        System.out.println("start to generate proxy class code");
        for (Map.Entry<String, AppLikeProxyClassCreator> entry : mMap.entrySet()) {
            String className = entry.getKey();
            AppLikeProxyClassCreator creator = entry.getValue();
            System.out.println("generate proxy class for " + className);

            //生成代理类，并写入到文件里，生成逻辑都在AppLikeProxyClassCreator里实现
            try {
                JavaFileObject jfo = processingEnv.getFiler().createSourceFile(creator.getProxyClassFullName());
                Writer writer = jfo.openWriter();
                writer.write(creator.generateJavaCode());
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return true;
    }
}
```

```
public class AppLikeProxyClassCreator {

    private Elements mElementUtils;
    private TypeElement mTypeElement;
    private String mProxyClassSimpleName;

    public AppLikeProxyClassCreator(Elements elements, TypeElement typeElement) {
        mElementUtils = elements;
        mTypeElement = typeElement;
        //代理类的名称，用到了之前定义过的前缀、后缀
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
     * 生成java代码，其实就是手动拼接，没有什么技术含量，比较繁琐，且容易出错
     * 可以采用第三方框架javapoet来实现，看自己需求了
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
```

那我们来实践一下，看看效果如何。
在app module里，创建2个类ModuleAAppLike、ModuleBAppLike，分别实现IAppLike接口，并采用AppLifeCycle注解。
在build.gradle里增加依赖引用：
```
dependencies {
    //---------其他依赖------------
    implementation project(':lifecycle-annotation')
    implementation project(':lifecycle-api')
    //需要注意这里是使用 annotationProcessor，即我们刚定义的注解处理器
    annotationProcessor project(':lifecycle-apt')
}
```
```
//实现了IAppLike接口，并且采用了AppLifeCycle注解，二者缺一不可，否则APT处理时会报错
@AppLifeCycle
public class ModuleAAppLike implements IAppLike {

    @Override
    public int getPriority() {
        return NORM_PRIORITY;
    }

    @Override
    public void onCreate(Context context) {
        Log.d("AppLike", "onCreate(): this is in ModuleAAppLike.");
    }

    @Override
    public void onTerminate() {
        Log.d("AppLike", "onTerminate(): this is in ModuleAAppLike.");
    }
}
```
将整个工程编译一下，可以看到在build目录下已经生成了我们定义的注解类，具体路径如下所示：

![](https://upload-images.jianshu.io/upload_images/5955727-a073364dd0400aee.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

看看代码是不是如我们所定义的一样：
```
package com.hm.iou.lifecycle.apt.proxy;

import android.content.Context;
import com.hm.lifecycle.api.IAppLike;
import com.hm.iou.lifecycle.demo.ModuleAAppLike;

public class Heima$$ModuleAAppLike$$Proxy implements IAppLike  {

  private ModuleAAppLike mAppLike;

  public Heima$$ModuleAAppLike$$Proxy() {
  mAppLike = new ModuleAAppLike();
  }

  public void onCreate(Context context) {
    mAppLike.onCreate(context);
  }

  public int getPriority() {
    return mAppLike.getPriority();
  }

  public void onTerminate() {
    mAppLike.onTerminate();
  }

}
```
关于APT技术，我这里不详解了，不了解的需要自行搜索相关资料来学习。

##### 3.4 扫描固定包下面的所有class
现在终于到了比较关键的一步了，在组件化开发过程中，如果有十多个组件里都有实现IAppLike接口的类，最终我们也会生成10多个代理类，这些代理类都是在同一个包下面。在组件集成到一个工程后，实际上只有一个apk安装包，所有编译后的class文件都被打包到dex文件里，应用运行时实际上是dalvik虚拟机(或者是ART)从dex文件里加载出class信息来运行的。

所以我们的思路是，运行时读取手机里的dex文件，从中读取出所有的class文件名，根据我们前面定义的代理类包名，来判断是不是我们的目标类，这样扫描一遍之后，就得到了固定包名下面所有类的类名了。具体实现，我采用了[Arouter](https://github.com/alibaba/ARouter/tree/master/arouter-api/src/main/java/com/alibaba/android/arouter/utils)框架里的代码，节选出部分核心代码说下：
```
public static Set<String> getFileNameByPackageName(Context context, final String packageName) throws PackageManager.NameNotFoundException, IOException, InterruptedException {
    final Set<String> classNames = new HashSet<>();
    //获取所有的class源文件，通常为classes.dex文件
    List<String> paths = getSourcePaths(context);
    final CountDownLatch parserCtl = new CountDownLatch(paths.size());

    for (final String path : paths) {
        //如果有多个dex文件，我们开启多个线程并发扫描
        DefaultPoolExecutor.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                DexFile dexfile = null;
                try {
                    if (path.endsWith(EXTRACTED_SUFFIX)) {
                        //NOT use new DexFile(path), because it will throw "permission error in /data/dalvik-cache"
                        dexfile = DexFile.loadDex(path, path + ".tmp", 0);
                    } else {
                        dexfile = new DexFile(path);
                    }

                    Enumeration<String> dexEntries = dexfile.entries();
                    while (dexEntries.hasMoreElements()) {
                        //遍历读取出所有的class名称，类的全限定名称
                        String className = dexEntries.nextElement();
                        //如果以我们指定的包名开头，则表示是我们的目标类
                        if (className.startsWith(packageName)) {
                            classNames.add(className);
                        }
                    }
                } catch (Throwable ignore) {
                } finally {
                    if (null != dexfile) {
                        try {
                            dexfile.close();
                        } catch (Throwable ignore) {
                        }
                    }
                    parserCtl.countDown();
                }
            }
        });
    }
    parserCtl.await();
    return classNames;
}
```

接下来，我们看看效果如何，修改__AppLifeCycleManager__类，在初始化时，增加扫描class的逻辑，主要代码逻辑如下：
```
    private static void scanClassFile(Context context) {
        try {
            //扫描到所有的目标类
            Set<String> set = ClassUtils.getFileNameByPackageName(context, LifeCycleConfig.PROXY_CLASS_PACKAGE_NAME);
            if (set != null) {
                for (String className : set) {
                    try {
                        //通过反射来加载实例化所有的代理类
                        Object obj = Class.forName(className).newInstance();
                        if (obj instanceof IAppLike) {
                            APP_LIKE_LIST.add((IAppLike) obj);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init(Context context) {
        scanClassFile(context);

        Collections.sort(APP_LIKE_LIST, new AppLikeComparator());
        for (IAppLike appLike : APP_LIKE_LIST) {
            appLike.onCreate(context);
        }
    }
```

到这里基本的功能已经实现了，我们可以自动加载注册所有组件的IAppLike类了，但是这里有个明显的性能问题，需要扫描dex文件里的所有class，通常一个安装包里，加上第三方库，class文件可能数以千计、数以万计，这让人有点杀鸡用牛刀的感觉。

每次应用冷启动时，都要读取一次dex文件并扫描全部class，这个性能损耗是很大的，我们可以做点优化，在扫描成功后将结果缓存下来，下次进来时直接读取缓存文件。

##### 3.5 通过gradle插件来动态插入字节码
前面介绍到的方法，不管怎样都需要在运行时读取dex文件，全量扫描所有的class。那么我们能不能在应用编译成apk时，就已经全量扫描过一次所有的class，并提取出所有实现了IAppLike接口的代理类呢，这样在应用运行时，效率就大大提升了。答案是肯定的，这就是gradle插件、动态插入java字节码技术。

关于gradle插件技术，具体实现请接着看下一章。

[Android组件化开发实践（九）：自定义Gradle插件](https://www.jianshu.com/p/3ec8e9574aaf)


__系列文章__
[Android组件化开发实践（一）：为什么要进行组件化开发？](https://www.jianshu.com/p/d0f5cf304fa4)<br/>
[Android组件化开发实践（二）：组件化架构设计](https://www.jianshu.com/p/06931c9b78dc)<br/>
[Android组件化开发实践（三）：组件开发规范](https://www.jianshu.com/p/027dabfd47ce)<br/>
[Android组件化开发实践（四）：组件间通信问题](https://www.jianshu.com/p/82b994fe532c)<br/>
[Android组件化开发实践（五）：组件生命周期管理](https://www.jianshu.com/p/65433846d38a)<br/>
[Android组件化开发实践（六）：老项目实施组件化](https://www.jianshu.com/p/4e3d189171e1)<br/>
[Android组件化开发实践（七）：开发常见问题及解决方案](https://www.jianshu.com/p/5f7feaf9f14f)<br/>
[Android组件化开发实践（八）：组件生命周期如何实现自动注册管理](https://www.jianshu.com/p/59368ce8b670)<br/>
[Android组件化开发实践（九）：自定义Gradle插件](https://www.jianshu.com/p/3ec8e9574aaf)<br/>