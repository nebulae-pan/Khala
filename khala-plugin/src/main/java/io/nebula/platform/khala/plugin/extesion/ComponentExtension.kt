package io.nebula.platform.khala.plugin.extesion

import io.nebula.platform.khala.plugin.utils.Log
import org.gradle.api.Action
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-07-12 12:00
 */
open class ComponentExtension {
    val dependencies = Dependencies()
    var buildOption = BuildOption()
//    var upload = Upload()
    var componentName: String? = null
    var uploadPath: String? = null
    var repoPath: String? = null
    var logLevel = 5
    var removeDuplicateResources = true

    var resourcePrefix: String? = null

    fun dependencies(action: Action<Dependencies>) {
        action.execute(dependencies)
    }

    fun buildOption(action: Action<BuildOption>) {
        action.execute(buildOption)
    }

    internal fun ensureComponentExtension(project: Project) {
        if (componentName == null) {
            componentName = project.name
        }
        if (uploadPath == null) {
            uploadPath = project.parent?.buildDir?.absolutePath
            if (uploadPath == null) {
                throw RuntimeException("got default build path error, please do not apply Khala plugin in root project")
            }
        }
        if (repoPath == null) {
            repoPath = uploadPath
        }
        if (resourcePrefix == null) {
            resourcePrefix = "${project.name}_"
        }
        Log.level = logLevel
    }
}