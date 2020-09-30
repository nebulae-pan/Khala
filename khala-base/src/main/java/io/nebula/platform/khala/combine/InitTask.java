package io.nebula.platform.khala.combine;

import java.util.Set;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 18:17
 */
public interface InitTask {
    void onDependency();

    void onConfigure();

    void onExecute();

    InitTask dependsOn(Object... dep);

    Set<Object> getDependsOn();

    Set<InitTask> getDependencyTasks();

    String getName();
}
