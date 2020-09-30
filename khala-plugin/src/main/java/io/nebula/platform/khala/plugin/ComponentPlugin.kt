package io.nebula.platform.khala.plugin

import io.nebula.platform.khala.plugin.extesion.ComponentExtension
import io.nebula.platform.khala.plugin.resolve.DuplicateHelper
import io.nebula.platform.khala.plugin.resolve.ZipHelper
import io.nebula.platform.khala.plugin.resolve.arsc.ArscFile
import io.nebula.platform.khala.plugin.resolve.arsc.StringPoolChunk
import io.nebula.platform.khala.plugin.resolve.arsc.TableChunk
import io.nebula.platform.khala.plugin.tasks.CommonLocalComponent
import io.nebula.platform.khala.plugin.tasks.transform.KhalaAppTransform
import io.nebula.platform.khala.plugin.tasks.transform.KhalaLibTransform
import io.nebula.platform.khala.plugin.utils.Descriptor
import io.nebula.platform.khala.plugin.utils.Log
import com.android.build.gradle.*
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.tasks.factory.registerTask
import net.sf.json.JSONArray
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.*
import java.util.*

/**
 * 插件初始化入口
 *
 * @author panxinghai
 *
 * date : 2019-07-11 11:19
 */
class ComponentPlugin : Plugin<Project> {
    private lateinit var mPluginExtension: ComponentExtension
    private lateinit var mProject: Project
    private lateinit var mTaskManager: TaskManager

    private var mLibConfigExecutor: (() -> Unit)? = null
    override fun apply(p: Project) {
        mProject = p
        Log.p("apply component plugin. ")
        p.plugins.apply("maven")
        if (isRunForAar()) {
            //作为生成aar包的情况
            mProject.afterEvaluate {
                mLibConfigExecutor?.invoke()
            }
            p.plugins.apply("com.android.library")
            val extension = mProject.extensions.findByType(BaseExtension::class.java)
            //注册Lib用Transform
            extension?.registerTransform(KhalaLibTransform(mProject))
            //library下有某些属性不允许赋值，委托给executor，在evaluate结束后清空这些值
            mLibConfigExecutor = {
                extension?.apply {
                    defaultConfig.applicationId = null
                    buildTypes {
                        it.all { buildType ->
                            buildType.isShrinkResources = false
                        }
                    }
                }
            }
        } else {
            p.plugins.apply("com.android.application")
            val extension = mProject.extensions.findByType(AppExtension::class.java)
            extension?.registerTransform(KhalaAppTransform(mProject))
            extension?.applicationVariants?.all {
                //release条件下执行资源去重操作
                if (it.buildType.name != "release") {
                    return@all
                }
                val variantData = (it as ApplicationVariantImpl).variantData
                val task = variantData.taskContainer.packageAndroidTask
                task?.get()?.doFirst {
                    val resourceArtifact = task.get().resourceFiles
                    resourceArtifact.files.forEach { file ->
                        replaceDuplicateResource(file)
                    }
                }
            }
        }
        mPluginExtension = mProject.extensions.create("component", ComponentExtension::class.java)
        mTaskManager = TaskManager(p, mPluginExtension)
        mProject.afterEvaluate {
            /* 在原本的插件任务结束后，执行该插件的初始化流程
             * 1.处理extension
             * 2.配置project（处理dependencies等等）
             * 3.创建自定义tasks
             */
            mPluginExtension.ensureComponentExtension(mProject)
            configureProject()
            createTasks()
        }
    }

    private fun configureProject() {
        Log.p(msg = "configure project.")
        val gradle = mProject.gradle
        val taskNames = gradle.startParameter.taskNames

        val needAddDependencies = needAddComponentDependencies(taskNames)

        mPluginExtension.dependencies.appendDependencies(mProject, needAddDependencies)
        mPluginExtension.dependencies.appendInterfaceApis(mProject, needAddDependencies)
    }

    /**
     * 判断本次执行流程的类型。特定情况下才会执行aar包的生成。
     * 情况如下：
     * 1.仅执行了一个task
     * 2.执行的task指定module为当前module 如 ./gradlew app:uploadComponent 指定的目标为app
     * 3.执行task为commonLocalComponent，这种情况会依次生成所有依赖该插件的module的aar包
     */
    private fun isRunForAar(): Boolean {
        val gradle = mProject.gradle
        val taskNames = gradle.startParameter.taskNames
        if (taskNames.size == 1) {
            if (mProject.name == "app") {
                //special module didn't use as library, it will change to white list
                return false
            }
            if (taskNames[0] == "commonLocalComponent") {
                //for all module load
                return true
            }
            val module = Descriptor.getTaskModuleName(taskNames[0])
            if (module != mProject.name) {
                return false
            }
            val taskName = Descriptor.getTaskNameWithoutModule(taskNames[0])
            return taskName.startsWith("uploadComponent") ||
                    taskName.startsWith("localComponent") ||
                    taskName.toLowerCase(Locale.getDefault()).startsWith("bundle") &&
                    taskName.toLowerCase(Locale.getDefault()).endsWith("aar")
        }
        return false
    }

    /**
     * 判断是否需要进行module的依赖，仅在最终构建结果是apk或aab（google play用）时进行依赖
     */
    private fun needAddComponentDependencies(taskNames: List<String>): Boolean {
        if (isRunForAar()) {
            return false
        }
        taskNames.forEach {
            val taskName = Descriptor.getTaskNameWithoutModule(it)
            if (taskName.startsWith("assemble") || taskName.startsWith("install") || taskName.startsWith("bundle")) {
                return true
            }
        }
        return false
    }

    private fun createTasks() {
        Log.p(msg = "create tasks.")
        //为每一个variant创建对应的task（除了test）
        if (isRunForAar()) {
            val libPlugin = mProject.plugins.getPlugin(LibraryPlugin::class.java) as BasePlugin<*>
            val variantManager = libPlugin.variantManager
            variantManager.variantScopes.forEach {
                val variantType = it.variantData.type
                if (variantType.isTestComponent) {
                    //continue , do not create task for test variant
                    return@forEach
                }

                val taskContainer = PluginTaskContainer()
                mTaskManager.pluginTaskContainer = taskContainer

                mTaskManager.createPrefixResourcesTask(it)

                mTaskManager.createGenerateSymbolTask(it)

                mTaskManager.createRefineManifestTask(it)

                mTaskManager.createGenInterfaceArtifactTask(it)

                //这里创建上传任务，结合maven仓库插件简化上传流程
                val gradle = mProject.gradle
                val taskNames = gradle.startParameter.taskNames
                val taskName = Descriptor.getTaskNameWithoutModule(taskNames[0])
                if (taskName.startsWith("uploadComponent")) {
                    mTaskManager.createUploadTask(it)
                } else {
                    mTaskManager.createLocalTask(it)
                }

                //插件中直接处理proguard，不需要外部添加
                mTaskManager.applyProguard(mProject, it)
            }
        } else {
            val appPlugin = mProject.plugins.getPlugin(AppPlugin::class.java) as BasePlugin<*>
            val variantManager = appPlugin.variantManager
            variantManager.variantScopes.forEach {
                //                mTaskManager.createReplaceManifestTask(it)

                mTaskManager.applyProguard(mProject, it)
            }
        }
        val rootProject = mProject.rootProject ?: return
        /* 这里是对commonLocalComponent的处理，通过对一系列localComponent和preBuild
         * 的依赖关系让整个task执行流程有序（也可以通过加锁互斥去处理）。
         * 算是对本地依赖的一种特殊兼容，如果以后分工程进行组件化，这个task完全可以去掉
         */
        if (rootProject.tasks.findByName("commonLocalComponent") == null) {
            val task = createCommonDeployTask(mProject)
            var lastTask: Task? = null
            rootProject.subprojects.last().afterEvaluate {
                rootProject.subprojects.forEach { p ->
                    val localComponent = p.tasks.find { it.name.startsWith("localComponent") }
                            ?: return@forEach
                    if (lastTask != null) {
                        val preBuildTask = p.tasks.find { it.name.startsWith("preBuild") }
                        preBuildTask?.dependsOn(lastTask)
                    }
                    lastTask = localComponent
                }
                if (lastTask != null) {
                    task?.get()?.dependsOn(lastTask)
                }
            }
        }
    }

    private fun createCommonDeployTask(project: Project): TaskProvider<CommonLocalComponent>? {
        val taskContainer = project.parent?.tasks ?: return null
        return taskContainer.registerTask(CommonLocalComponent.ConfigAction(project))
    }

    private fun replaceDuplicateResource(file: File) {
        val apFileName = readJsonFileAndGetPathValue(File(file, "output.json"))
        val zipFile = File(file, apFileName)
        var arscFile: File? = null
        val resourceFile: ArscFile?
        try {
            if (!zipFile.exists()) {
                println("file$zipFile not exist")
                return
            }
            arscFile = ZipHelper.unzipSpecialFile(zipFile, "resources.arsc", zipFile.parent)
            val duplicateCollection = DuplicateHelper.checkDuplicate(zipFile)
            val replaceTargetMap = mutableMapOf<String, String>()
            duplicateCollection.forEach {
                val target = it.value[0]
                for (i in 1 until it.value.size) {
                    replaceTargetMap[it.value[i]] = target
                    ZipHelper.removeZipEntry(zipFile, it.value[i])
                }
            }
            resourceFile = ArscFile.fromFile(arscFile)
            val chunks = resourceFile.chunks
            chunks.forEach {
                if (it is TableChunk) {
                    val stringPool = it.chunks[0] as StringPoolChunk
                    for (i in 0 until stringPool.stringCount) {
                        val key = stringPool.getString(i)
                        if (replaceTargetMap.containsKey(key)) {
                            replaceTargetMap[key]?.let { it1 -> stringPool.setString(i, it1) }
//                            println("replace $key to ${replaceTargetMap[key]}")
                        }
                    }
                }
            }

            arscFile.delete()
            arscFile = File(file, "resources.arsc")
            arscFile.createNewFile()

            val fis = FileOutputStream(arscFile)
            fis.use {
                fis.write(resourceFile.toByteArray())
            }

            ZipHelper.addZipEntry(zipFile, arscFile, "resources.arsc")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            arscFile?.delete()
        }

    }

    private fun readPlaintTextFile(file: File): String {
        val bufferedReader: BufferedReader
        val sb = StringBuilder()
        var tempString: String?
        val fileInputStream = FileInputStream(file)
        val inputStreamReader = InputStreamReader(fileInputStream, "UTF-8")
        bufferedReader = BufferedReader(inputStreamReader)
        bufferedReader.use { reader ->
            while (reader.readLine().also { tempString = it } != null) {
                sb.append(tempString)
            }
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    fun readJsonFileAndGetPathValue(file: File): String {
        val jsonString = readPlaintTextFile(file)
        val jsonArray = JSONArray.fromObject(jsonString)
        val jsonObject = jsonArray.getJSONObject(0)
        return jsonObject.getString("path")
    }
}