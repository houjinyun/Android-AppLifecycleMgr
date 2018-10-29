package com.hm.lifecycle.api;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.hm.iou.lifecycle.annotation.LifeCycleConfig;
import com.hm.lifecycle.api.utils.ClassUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Created by hjy on 2018/10/23.
 */

public class AppLifeCycleManager {

    public static boolean DEBUG = false;

    private static List<IAppLike> APP_LIKE_LIST = new ArrayList<>();
    private static boolean REGISTER_BY_PLUGIN = false;
    private static boolean INIT = false;

    /**
     * 通过插件加载 IAppLike 类
     */
    private static void loadAppLike() {
    }

    private static void registerAppLike(String className) {
        if (TextUtils.isEmpty(className))
            return;
        try {
            Object obj = Class.forName(className).getConstructor().newInstance();
            if (obj instanceof IAppLike) {
                registerAppLike((IAppLike) obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册IAppLike
     *
     */
    private static void registerAppLike(IAppLike appLike) {
        //标志我们已经通过插件注入代码了
        REGISTER_BY_PLUGIN = true;
        APP_LIKE_LIST.add(appLike);
    }

    /**
     * 初始化
     *
     * @param context
     */
    public static void init(Context context) {
        if (INIT)
            return;
        INIT = true;
        loadAppLike();
        if (!REGISTER_BY_PLUGIN) {
            Log.d("AppLike", "需要扫描所有类...");
            scanClassFile(context);
        } else {
            Log.d("AppLike", "插件里已自动注册...");
        }

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
     * 扫描出固定包名下，实现了IAppLike接口的代理类
     *
     * @param context
     */
    private static void scanClassFile(Context context) {
        try {
            Set<String> set = ClassUtils.getFileNameByPackageName(context, LifeCycleConfig.PROXY_CLASS_PACKAGE_NAME);
            if (set != null) {
                for (String className : set) {
                    if (DEBUG) {
                        Log.d("AppLifeCycle", className);
                    }
                    try {
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
