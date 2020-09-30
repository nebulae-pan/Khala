package io.nebula.platform.khala.plugin.tasks

import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * @author panxinghai
 *
 * date : 2020-04-26 13:58
 */
open class CommonLocalComponent : DefaultTask() {

    @TaskAction
    fun taskAction() {

    }

    class ConfigAction(val project: Project)
        : TaskCreationAction<CommonLocalComponent>() {
        override val name: String
            get() = "commonLocalComponent"
        override val type: Class<CommonLocalComponent>
            get() = CommonLocalComponent::class.java

        override fun configure(task: CommonLocalComponent) {

        }
    }
}