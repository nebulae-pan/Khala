package io.nebula.platform.khala.combine;

import java.util.List;

/**
 * @author panxinghai
 * <p>
 * date : 2019-10-16 17:58
 */
public interface IComponentTaskProvider {
    List<InitTask> getComponentTasks();
}
