package io.nebula.platform.khala.combine;

import java.util.List;

/**
 * 用于收集InitTask的transform，编译过程中代码生成的模版接口
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-14 22:39
 */
public interface ITaskCollector {
    List<InitTask> gatherTasks();
}
