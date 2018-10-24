package com.hm.iou.lifecycle.demo;

import android.content.Context;
import android.util.Log;

import com.hm.iou.lifecycle.annotation.AppLifeCycle;
import com.hm.lifecycle.api.IAppLike;

/**
 * Created by hjy on 2018/10/23.
 */
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
