package io.nebula.platform.khala.plugin

import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * @author panxinghai
 *
 * date : 2019-07-12 17:38
 */
class PluginTaskFactory(private val factory: TaskFactory) : TaskFactory by factory {

    override fun <T : Task> register(creationAction: TaskCreationAction<T>): TaskProvider<T> {
        val taskProvider = factory.register(creationAction)
        taskProvider.get().group = "component"
        return taskProvider
    }

    override fun register(name: String): TaskProvider<Task> {
        val taskProvider = factory.register(name)
        taskProvider.get().group = "component"
        return taskProvider
    }
}