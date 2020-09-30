package io.nebula.platform.khala.plugin.tasks

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.maven.MavenDeployer
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Upload
import java.io.File

/**
 * Created by nebula on 2019-07-21
 *
 * reference [MavenPlugin]
 */
open class UploadComponent : Upload() {
    class ConfigAction(private val scope: VariantScope,
                       private val project: Project) :
            TaskCreationAction<UploadComponent>() {
        override val name: String
            get() = "uploadComponent${scope.variantConfiguration.flavorName.capitalize()}"


        override val type: Class<UploadComponent>
            get() = UploadComponent::class.java

        override fun configure(task: UploadComponent) {
            val config = this.project.configurations.getByName("archives")
            createUploadTask(task, config.uploadTaskName, config, project)
            val uploadArchives = project.tasks.withType(Upload::class.java).findByName("uploadArchives")

            uploadArchives?.repositories?.forEach {
                task.repositories.add(it)
            }
            task.repositories.withType(MavenDeployer::class.java) {
                //                val remote = MavenRemoteRepository()
//                remote.url = project.uri("../resultRepo/").toString()
//                it.repository = remote
                it.pom.apply {
                    artifactId = project.name
                }
            }
        }

        private fun createUploadTask(upload: Upload, name: String, configuration: Configuration, project: Project) {
            upload.description = "Uploads all artifacts belonging to $configuration"
            upload.configuration = configuration
            upload.isUploadDescriptor = true
            upload.conventionMapping.map("descriptorDestination") { File(project.buildDir, "ivy.xml") }
        }

        private fun getField(instance: Any, fieldName: String): Any {
            val field = instance.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            return field.get(instance)
        }
    }
}