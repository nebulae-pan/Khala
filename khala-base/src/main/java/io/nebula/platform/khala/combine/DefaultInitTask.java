package io.nebula.platform.khala.combine;

import java.util.HashSet;
import java.util.Set;

import io.nebula.platform.khala.annotation.TaskIgnore;

/**
 * 默认的{@link InitTask}实现类，处理了依赖的实现，使用{@link InitTask}接口时推荐直接实现该类
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-14 21:11
 */
@TaskIgnore
public abstract class DefaultInitTask implements InitTask {
    private Set<Object> mTaskDependsOn = new HashSet<>();
    private Set<InitTask> mDependencyTasks = new HashSet<>();
    private InitTaskManager mTaskManager = InitTaskManager.instance();

    @Override
    public void onDependency() {

    }

    @Override
    public void onConfigure() {

    }

    @Override
    public void onExecute() {

    }

    @Override
    public InitTask dependsOn(Object... dep) {
        for (Object object : dep) {
            if (object instanceof String) {
                InitTask task = mTaskManager.findByName((String) object);
                if (task == null) {
                    throw new RuntimeException("cannot find task named '" + object + "'.");
                }
                mDependencyTasks.add(task);
                mTaskDependsOn.add(object);
                continue;
            }
            if (object instanceof InitTask) {
                mDependencyTasks.add((InitTask) object);
                mTaskDependsOn.add(object);
            }
        }
        return this;
    }

    @Override
    public Set<Object> getDependsOn() {
        return mTaskDependsOn;
    }

    @Override
    public Set<InitTask> getDependencyTasks() {
        return mDependencyTasks;
    }

}
