package com.bilibili;

import android.app.ActivityManager;
import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import com.bilibili.di.component.ActivityComponent;
import com.bilibili.di.component.ApiComponent;
import com.bilibili.di.component.DaggerActivityComponent;
import com.bilibili.di.component.DaggerApiComponent;
import com.bilibili.di.component.DaggerFragmentComponent;
import com.bilibili.di.component.FragmentComponent;
import com.bilibili.di.module.ApiModule;
import com.bilibili.di.module.FragmentModule;
import com.bilibili.widget.CustomBitmapMemoryCacheParamsSupplier;
import com.common.app.ActivityLifecycleManager;
import com.common.app.AppComponent;
import com.common.app.DaggerAppComponent;
import com.common.util.Utils;
import com.facebook.common.memory.MemoryTrimType;
import com.facebook.common.memory.MemoryTrimmable;
import com.facebook.common.memory.MemoryTrimmableRegistry;
import com.facebook.common.memory.NoOpMemoryTrimmableRegistry;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipelineConfig;

import me.yokeyword.fragmentation.BuildConfig;
import me.yokeyword.fragmentation.Fragmentation;
import me.yokeyword.fragmentation.helper.ExceptionHandler;

public class App extends Application {

    private static App sInstance;

    public static synchronized App getInstance() {
        return sInstance;
    }

    private static AppComponent sAppComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        // 注册一个回调监听，lifecycle 发生变化会触发里面的函数
        registerActivityLifecycleCallbacks(new ActivityLifecycleManager());
        Fragmentation.builder()
                // 设置 栈视图 模式为 悬浮球模式   SHAKE: 摇一摇唤出   NONE：隐藏
                .stackViewMode(Fragmentation.NONE)
                // ture时，遇到异常："Can not perform this action after onSaveInstanceState!"时，会抛出
                // false时，不会抛出，会捕获，可以在handleException()里监听到
                .debug(BuildConfig.DEBUG)
                // 在debug=false时，即线上环境时，上述异常会被捕获并回调ExceptionHandler
                .handleException(new ExceptionHandler() {
                    @Override
                    public void onException(Exception e) {
                        // 建议在该回调处上传至我们的Crash监测服务器
                        // 以Bugtags为例子: 手动把捕获到的 Exception 传到 Bugtags 后台。
                        // Bugtags.sendException(e);
                    }
                })
                .install();
        initFresco();
        if (sAppComponent == null) {
            sAppComponent = DaggerAppComponent.create();
        }
        //初始化工具类
        Utils.init(this);
    }

    /**
     * 这段代码的意义是在使用Fresco图片加载库时进行内存管理和优化
     * 通过反馈系统内存调整信息，动态地管理Fresco的内存使用，并进行相关的优化，以提升应用程序的性能和资源利用效率。
     */
    public void initFresco() {
        //当内存紧张时采取的措施 创建一个MemoryTrimmableRegistry对象，并将其设置为不执行任何操作的实例。
        MemoryTrimmableRegistry memoryTrimmableRegistry = NoOpMemoryTrimmableRegistry.getInstance();
        //注册一个MemoryTrimmable接口的匿名实现类，在内存发生调整时触发回调方法。
        memoryTrimmableRegistry.registerMemoryTrimmable(new MemoryTrimmable() {
            @Override
            public void trim(MemoryTrimType trimType) {
                // 在回调方法中，根据内存调整类型的建议比例，判断是否需要清除内存缓存。如果应用程序靠近Dalvik堆限制、在后台时系统内存较低、在前台时系统内存较低，则清除Fresco的内存缓存。
                final double suggestedTrimRatio = trimType.getSuggestedTrimRatio();
                Log.d("Fresco", String.format("onCreate suggestedTrimRatio : %d", suggestedTrimRatio));
                if (MemoryTrimType.OnCloseToDalvikHeapLimit.getSuggestedTrimRatio() == suggestedTrimRatio
                        || MemoryTrimType.OnSystemLowMemoryWhileAppInBackground.getSuggestedTrimRatio() == suggestedTrimRatio
                        || MemoryTrimType.OnSystemLowMemoryWhileAppInForeground.getSuggestedTrimRatio() == suggestedTrimRatio
                        ) {
                    //清除内存缓存
                    Fresco.getImagePipeline().clearMemoryCaches();
                }
            }
        });
        // 创建ImagePipelineConfig对象，并设置一些配置参数，包括启用图像降低采样、对网络请求的图片进行大小调整和旋转等。
        ImagePipelineConfig config = ImagePipelineConfig.newBuilder(this)
                .setDownsampleEnabled(true)
                //为网络设置调整大小和旋转启用
                .setResizeAndRotateEnabledForNetwork(true)
//                .setBitmapMemoryCacheParamsSupplier(new DefaultBitmapMemoryCacheParamsSupplier((ActivityManager) getSystemService(ACTIVITY_SERVICE)))
//                .setBitmapMemoryCacheParamsSupplier(new DefaultEncodedMemoryCacheParamsSupplier())
                //设置位图内存缓存参数供应商
                .setBitmapMemoryCacheParamsSupplier(new CustomBitmapMemoryCacheParamsSupplier((ActivityManager) getSystemService(ACTIVITY_SERVICE)))
                .setMemoryTrimmableRegistry(memoryTrimmableRegistry)
                .setBitmapsConfig(Bitmap.Config.RGB_565)
                .build();
        Fresco.initialize(this, config);
    }

    public AppComponent getAppComponent() {
        return sAppComponent;
    }

    public ActivityComponent getActivityComponent() {
        return DaggerActivityComponent.builder()
                .apiComponent(getApiComponent())
                .build();
    }

    public FragmentComponent getFragmentComponent() {
        return DaggerFragmentComponent.builder()
                .apiComponent(getApiComponent())
                .fragmentModule(new FragmentModule())
                .build();
    }

    private ApiComponent getApiComponent() {
        return DaggerApiComponent.builder()
                .appComponent(getAppComponent())
                .apiModule(new ApiModule())
                .build();
    }
}
