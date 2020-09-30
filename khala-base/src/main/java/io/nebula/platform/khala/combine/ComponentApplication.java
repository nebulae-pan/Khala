package io.nebula.platform.khala.combine;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;

import java.util.Set;

/**
 * 需要加入到组件化初始化序列的Application需要实现该类。
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-12 11:22
 */
@SuppressLint("NewApi")
@SuppressWarnings("unused")
abstract public class ComponentApplication extends Application
        implements InitTask {
    private Application mHost;
    private boolean mIsRunAsApplication = false;
    private DefaultInitTask mDelegate;
    private String mName;

    public ComponentApplication() {
        mDelegate = new DefaultInitTask() {
            @Override
            public String getName() {
                return "";
            }
        };
    }


    public void setHostApplication(Application application) {
        mHost = application;
    }

    public void setNameByComponentName(String name) {
        mName = name;
    }

    public void setRunAsApplication(boolean runAsApplication) {
        mIsRunAsApplication = runAsApplication;
    }

    public abstract void initAsApplication();

    public abstract void initAsComponent(Application realApplication);

    @Override
    public void onConfigure() {

    }

    @Override
    public void onDependency() {

    }

    @Override
    final public Set<Object> getDependsOn() {
        return mDelegate.getDependsOn();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    final public InitTask dependsOn(Object... dep) {
        return mDelegate.dependsOn(dep);
    }

    @Override
    final public Set<InitTask> getDependencyTasks() {
        return mDelegate.getDependencyTasks();
    }

    @Override
    public void onExecute() {
        if (mIsRunAsApplication) {
            initAsApplication();
        } else {
            initAsComponent(mHost);
        }
    }

    @Override
    public void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (mHost != null) {
            mHost.registerActivityLifecycleCallbacks(callback);
        }
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (mHost != null) {
            mHost.registerComponentCallbacks(callback);
        }
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        if (mHost != null) {
            mHost.unregisterComponentCallbacks(callback);
        }
    }

    @Override
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (mHost != null) {
            mHost.unregisterActivityLifecycleCallbacks(callback);
        }
    }

    @Override
    public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (mHost != null) {
            mHost.registerOnProvideAssistDataListener(callback);
        }
    }

    @Override
    public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
        if (mHost != null) {
            mHost.unregisterOnProvideAssistDataListener(callback);
        }
    }
}
