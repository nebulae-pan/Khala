package io.nebula.platform.khala.plugin.tasks.transform

import com.android.build.api.transform.Format
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import io.nebula.platform.khala.Constants
import io.nebula.platform.khala.annotation.TaskIgnore
import io.nebula.platform.khala.combine.IComponentTaskProvider
import io.nebula.platform.khala.combine.ITaskCollector
import io.nebula.platform.khala.combine.InitTask
import io.nebula.platform.khala.plugin.extesion.ComponentExtension
import io.nebula.platform.khala.plugin.utils.*
import io.nebula.platform.khala.plugin.utils.javassist.MethodGen
import javassist.CtClass
import org.gradle.api.Project
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipEntry

/**
 * 构建多模块初始化的transform，可能还有问题，使用前多多调试。
 * 多模块间的引用类似路由的设计，参考[RouterCompileActuator]。
 * 这里将所有的[InitTask]实现类收集，并在运行时交由[KhalaApplication]执行。
 * 收集逻辑可以参考路由部分。
 *
 * @author panxinghai
 *
 * date : 2019-11-19 10:20
 */
class InitTaskCompileActuator(private val project: Project,
                              isComponent: Boolean) : TypeActuator(isComponent) {
    private lateinit var mInitTaskCtClass: CtClass

    @Persistent
    private val mTaskClassList = arrayListOf<CtClass>()

    @Persistent
    private val mComponentTaskProviderList = arrayListOf<String>()

    private val mTaskNameListProvider: (CtClass, Status) -> Unit = { ctClass, status ->
        if (!Modifier.isAbstract(ctClass.modifiers) && !ctClass.hasAnnotation(TaskIgnore::class.java)) {
            if (status == Status.ADDED && mTaskClassList.contains(ctClass)) {
                throw RuntimeException("incremental error: ${ctClass.name} has already add " +
                        "in last build.")
            }
            mTaskClassList.add(ctClass)
        }
    }

    override fun preTraversal(transformInvocation: TransformInvocation) {
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        mInitTaskCtClass = InjectHelper.instance.getClassPool()[InitTask::class.java.name]
        if (transformInvocation.isIncremental) {
            IncrementalHelper.loadPersistentField(this, transformInvocation.persistenceOutputDir())
        }
    }

    override fun onClassVisited(ctClass: CtClass): Boolean {
        return onIncrementalClassVisited(Status.ADDED, ctClass)
    }

    override fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean {
        //for task auto gather and inject
        if (ctClass.subtypeOf(mInitTaskCtClass)) {
            mTaskNameListProvider.invoke(ctClass, status)
        }
        return false
    }

    override fun onRemovedClassVisited(ctClass: CtClass) {
        if (ctClass.subtypeOf(mInitTaskCtClass)) {
            mTaskClassList.remove(ctClass)
        }
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry, jarFile: File) {
        if (isComponent) {
            return
        }
        if (zipEntry.name.startsWith(Constants.INIT_TASK_GEN_FILE_FOLDER)) {
            Log.d("found task:${zipEntry.name}")
            mComponentTaskProviderList.add(getClassNameWithEntryName(zipEntry.name))
            return
        }
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        IncrementalHelper.savePersistentField(this, transformInvocation.persistenceOutputDir())
        val dest = transformInvocation.outputProvider.getContentLocation(
                "injectGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
        if (isComponent) {
            if (mTaskClassList.isNotEmpty()) {
                genComponentTaskProviderImpl(dest)
            }
            return
        }
        if (mTaskClassList.isNotEmpty() || mComponentTaskProviderList.isNotEmpty()) {
            genTaskCollectorImpl(dest)
        }
    }


    /**
     * generate componentTask Provider implementation. see [IComponentTaskProvider].
     * @param dir target directory to save transform result
     */
    private fun genComponentTaskProviderImpl(dir: File) {
        MethodGen(Constants.INIT_TASK_GEN_FILE_PACKAGE + genComponentTaskProviderClassName())
                .signature(returnStatement = "public java.util.List",
                        name = "getComponentTasks")
                .interfaces(IComponentTaskProvider::class.java)
                .body { genGatherComponentTaskMethodBody() }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun genGatherComponentTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskClassList.forEach {
            if (isSubtypeOf(it, Constants.COMPONENT_APPLICATION_NAME)) {
                sb.append("${it.name} \$_ = new ${it.name}();")
                        .append("\$_.setNameByComponentName(\"${getComponentExtension().componentName}\");")
                        .append("list.add(\$_);")
            } else {
                sb.append("list.add(new ${it.name}());")
            }
        }
        sb.append("return list;}")
        return sb.toString()
    }

    /**
     * generate taskCollector implementation. see [ITaskCollector]
     * @param dir target directory to save transform result
     */
    private fun genTaskCollectorImpl(dir: File) {
        MethodGen(Constants.INIT_TASK_GEN_FILE_PACKAGE + Constants.INIT_TASK_COLLECTOR_IMPL_NAME)
                .signature(returnStatement = "public java.util.List",
                        name = "gatherTasks")
                .interfaces(ITaskCollector::class.java)
                .body { genGatherTaskMethodBody() }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun genGatherTaskMethodBody(): String {
        val sb = StringBuilder("{java.util.ArrayList list = new java.util.ArrayList();")
        mTaskClassList.forEach {
            if (isSubtypeOf(it, Constants.COMPONENT_APPLICATION_NAME)) {
                sb.append("${it.name} \$_ = new ${it.name}();")
                        .append("\$_.setNameByComponentName(\"${getComponentExtension().componentName}\");")
                        .append("list.add(\$_);")
            } else {
                sb.append("list.add(new ${it.name}());")
            }
        }
        mComponentTaskProviderList.forEach {
            sb.append("list.addAll(new $it().getComponentTasks());")
        }
        sb.append("return list;}")
        return sb.toString()
    }

    private fun isSubtypeOf(ctClass: CtClass, superType: String): Boolean {
        var ct = ctClass
        while (ct.superclass.name != "java.lang.Object") {
            if (ct.name == superType) {
                return true
            }
            ct = ct.superclass
        }
        return false
    }

    private fun getClassNameWithEntryName(name: String): String {
        return name.split('.').first().replace('/', '.')
    }

    private fun genComponentTaskProviderClassName(): String {
        val componentName = getComponentExtension().componentName
        return "$componentName\$\$TaskProvider"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw RuntimeException("can not find ComponentExtension, please check your build.gradle file")
    }
}