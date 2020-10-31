package io.nebula.platform.khala.plugin

import io.nebula.platform.khala.plugin.extesion.ComponentExtension
import io.nebula.platform.khala.plugin.tasks.*
import io.nebula.platform.khala.plugin.utils.Descriptor
import com.android.SdkConstants
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.TaskFactory
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.transforms.BaseProguardAction
import com.android.build.gradle.internal.variant.VariantHelper
import com.android.build.gradle.tasks.ProcessLibraryManifest
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.*

/**
 * 创建task的管理类，每个task的用途参见其本身注释
 *
 * @author panxinghai
 *
 * date : 2019-07-11 14:22
 */
class TaskManager(private val project: Project,
                  private val extension: ComponentExtension
) {
    private var taskFactory: TaskFactory = PluginTaskFactory(TaskFactoryImpl(project.tasks))
    var pluginTaskContainer: PluginTaskContainer? = null

    fun createRefineManifestTask(scope: VariantScope) {
        val processTask = scope.taskContainer.processManifestTask?.get()
        if (processTask !is ProcessLibraryManifest) {
            return
        }
        val manifestOutput = processTask.manifestOutputFile.get().asFile
                ?: return
        val task = taskFactory.register(RefineManifest.ConfigAction(scope, manifestOutput))
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
    }

    fun createPrefixResourcesTask(scope: VariantScope) {
        val file = scope.getIntermediateDir(InternalArtifactType.PACKAGED_RES)
        var prefix = extension.resourcePrefix
        if (prefix == null) {
            prefix = "${project.name}_"
        }
        prefix = prefix.replace('-', '_')
        val task = taskFactory.register(PrefixResources.ConfigAction(scope, file, prefix))
        task.get().dependsOn(scope.taskContainer.mergeResourcesTask)
        pluginTaskContainer?.prefixResources = task.get()
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
//        //use ParseLibraryResourcesTask's output
//        val parseResourcesTaskName = scope.getTaskName("parse", "LibraryResources")
//        val parseResourcesTask = project.tasks.getByName(parseResourcesTaskName)
//        parseResourcesTask.dependsOn(task)
    }

    fun createGenerateSymbolTask(scope: VariantScope) {
        val dir = File(scope.globalScope.intermediatesDir,
                "symbols/" + scope.variantData.variantConfiguration.dirName)
        val symbol = File(dir, SdkConstants.FN_RESOURCE_TEXT)
        val symbolTableWithPackageName = FileUtils.join(
                scope.globalScope.intermediatesDir,
                SdkConstants.FD_RES,
                "symbol-table-with-package",
                scope.variantConfiguration.dirName,
                "package-aware-r.txt")
        val task = taskFactory.register(GenerateSymbol.CreationAction(scope,
                symbol,
                symbolTableWithPackageName,
                File(scope.globalScope.intermediatesDir, "Khala-PrefixRFile")))
        scope.artifacts.createBuildableArtifact(
                InternalArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME,
                BuildArtifactsHolder.OperationType.TRANSFORM,
                ImmutableList.of(symbol),
                task.name
        )
        task.get().dependsOn(pluginTaskContainer?.prefixResources)
        scope.taskContainer.bundleLibraryTask?.get()?.dependsOn(task)
        pluginTaskContainer?.generateSymbol = task.get()
//
//        val kotlinCompileTask = project.tasks.findByName("compile${scope.fullVariantName.capitalize()}Kotlin")
//        if (scope.variantConfiguration.buildType.name == "release") {
//            task.get().dependsOn.forEach {
//                println("RFile:$it")
//            }
//            println(kotlinCompileTask)
//            kotlinCompileTask!!.dependsOn.forEach {
//                println(it)
//            }
////            task.get().dependsOn(kotlinCompileTask)
//            project.afterEvaluate {
//                kotlinCompileTask!!.dependsOn.forEach {
//                    println(it)
//                }
//            }
//        }

    }

    fun createGenInterfaceArtifactTask(scope: VariantScope) {
        val javaOutput = scope.artifacts.getFinalProduct<FileSystemLocation>(InternalArtifactType.JAVAC).get().asFile
        val kotlinOutput = (project.tasks.findByName("compile${scope.fullVariantName.capitalize()}Kotlin") as? KotlinCompile)?.destinationDir
        val task = taskFactory.register(GenerateInterfaceArtifact.ConfigAction(scope, javaOutput, kotlinOutput))
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        pluginTaskContainer?.genInterface = task.get()
        if (scope.variantConfiguration.buildType.name != "release") {
            return
        }
        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                project.artifacts.add("archives", File(task.get().destDir, "interface.jar"))
            }
        }
    }

    fun createReplaceManifestTask(scope: VariantScope) {
        val manifestFile = scope.taskContainer.packageAndroidTask?.get()?.manifests?.get()?.asFile
                ?: return
        val task = taskFactory.register(ReplaceManifest.ConfigAction(scope, File(manifestFile, "AndroidManifest.xml")))
        scope.taskContainer.processAndroidResTask?.get()?.dependsOn(task.get())
        task.get().dependsOn(scope.taskContainer.processManifestTask)
    }

    fun createUploadTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release") {
            return
        }

        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", scope.taskContainer.bundleLibraryTask!!)
            }
        }
        val task = taskFactory.register(UploadComponent.ConfigAction(scope, project))
        pluginTaskContainer?.uploadTask = task.get()
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        task.get().dependsOn(pluginTaskContainer?.genInterface!!)
    }

    fun createLocalTask(scope: VariantScope) {
        if (scope.variantConfiguration.buildType.name != "release") {
            return
        }

        if (project.gradle.startParameter.taskNames.size == 0) {
            return
        }
        val flavor = getFlavor()
        if (flavor != null) {
            if (flavor.toLowerCase(Locale.getDefault()) == scope.variantConfiguration.flavorName) {
                VariantHelper.setupArchivesConfig(project, scope.variantDependencies.runtimeClasspath)
                project.artifacts.add("archives", scope.taskContainer.bundleLibraryTask!!)
            }
        }
        val task = taskFactory.register(LocalComponent.ConfigAction(scope, project))
        pluginTaskContainer?.uploadTask = task.get()
        task.get().dependsOn(scope.taskContainer.bundleLibraryTask)
        task.get().dependsOn(pluginTaskContainer?.genInterface!!)
    }

    fun applyProguard(project: Project, scope: VariantScope) {
        val proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${scope.fullVariantName.capitalize()}")
        if (proguardTask is TransformTask) {
            val transform = proguardTask.transform
            if (transform is BaseProguardAction) {
                transform.keep("public class io.nebula.platform.khala.gen.** {*;}")
                transform.dontwarn("${scope.variantData.applicationId}.R$*")
            }
        }
    }

    private fun getFlavor(): String? {
        val uploadTaskPrefix = "uploadComponent"
        val localTaskPrefix = "localComponent"
        val commonTaskPrefix = "commonLocalComponent"

        val startTaskName = Descriptor.getTaskNameWithoutModule(project.gradle.startParameter.taskNames[0])
        if (startTaskName == commonTaskPrefix) {
            //todo:consider add flavor
            return ""
        }
        if (startTaskName.startsWith(localTaskPrefix)) {
            return startTaskName.substring(localTaskPrefix.length)
        }
        if (startTaskName.startsWith(uploadTaskPrefix)) {
            return startTaskName.substring(uploadTaskPrefix.length)
        }
        return null
    }
}