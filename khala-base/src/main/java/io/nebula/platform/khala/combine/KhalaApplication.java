package io.nebula.platform.khala.combine;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.nebula.platform.khala.Constants;
import io.nebula.platform.khala.Khala;

/**
 * 实际运行的Application，这里会收集所有需要初始化的逻辑（实现了{@link InitTask}的类），并统一按序执行。
 * 可以考虑新增不同线程执行任务的功能。
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-15 15:45
 */
public class KhalaApplication extends Application {
    private List<ComponentApplication> mComponentApplications;
    private List<InitTask> mInitTasks;

    @Override
    public void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mComponentApplications = new ArrayList<>();
        mInitTasks = Khala.instance().getTaskManager().getExecuteTaskList();
        String realAppName = getRealAppName();
        for (InitTask task : mInitTasks) {
            Log.e("khala", "execute init task:" + task.getName());
            if (task instanceof ComponentApplication) {
                ComponentApplication componentApplication = (ComponentApplication) task;
                if (componentApplication.getClass().getName().equals(realAppName)) {
                    componentApplication.setRunAsApplication(true);
                }
                componentApplication.setHostApplication(this);
                mComponentApplications.add(componentApplication);
                componentApplication.attachBaseContext(base);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        for (InitTask task : mInitTasks) {
            if (task instanceof ComponentApplication) {
                ((ComponentApplication) task).onCreate();
            }
            task.onExecute();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (ComponentApplication component : mComponentApplications) {
            component.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        for (ComponentApplication component : mComponentApplications) {
            component.onLowMemory();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        for (ComponentApplication component : mComponentApplications) {
            component.onTerminate();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        for (ComponentApplication component : mComponentApplications) {
            component.onTrimMemory(level);
        }
    }

    private String getRealAppName() {
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            return appInfo.metaData.getString(Constants.REPLACE_META_NAME);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
