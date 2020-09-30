package io.nebula.platform.khala.plugin.tasks.transform

import com.android.build.api.transform.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import io.nebula.platform.khala.Constants
import io.nebula.platform.khala.annotation.Inject
import io.nebula.platform.khala.annotation.Router
import io.nebula.platform.khala.exception.InjectTypeException
import io.nebula.platform.khala.node.NodeType
import io.nebula.platform.khala.node.RouterNode
import io.nebula.platform.khala.plugin.exception.RouterPathDuplicateException
import io.nebula.platform.khala.plugin.extesion.ComponentExtension
import io.nebula.platform.khala.plugin.manager.InjectType
import io.nebula.platform.khala.plugin.resolve.ZipHelper
import io.nebula.platform.khala.plugin.utils.*
import io.nebula.platform.khala.plugin.utils.javassist.MethodGen
import io.nebula.platform.khala.template.IRouterLazyLoader
import io.nebula.platform.khala.template.IRouterNodeProvider
import io.nebula.platform.khala.template.IServiceAliasProvider
import io.nebula.platform.khala.util.CollectionHelper
import javassist.*
import javassist.bytecode.FieldInfo
import javassist.bytecode.SignatureAttribute
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.zip.ZipEntry

/**
 * All router relative code is in here.This class help inject code for Router navigate.
 * This actuator support compile Activity, Fragment, IComponentService Node now.
 * @author panxinghai
 *
 * date : 2019-11-18 18:44
 */
class RouterCompileActuator(private val project: Project,
                            isComponent: Boolean) : TypeActuator(isComponent) {
    @Persistent("NodeInfoSet")
    private var nodeSetByGroup = NodeInfoSetGroupSplit()

    @Persistent
    private var libProviderConstants = LibProviderContainer()

    @Persistent
    private var duplicateChecker: DuplicateChecker? = null

    private var baseClassLoader: URLClassLoader? = null

    private var checkDuplicate: Boolean = false

    override fun preTraversal(transformInvocation: TransformInvocation) {
        InjectHelper.instance.refresh()
        InjectHelper.instance.appendAndroidPlatformPath(project)
        if (isComponent) {
            val variantName = transformInvocation.context.variantName
            val libPlugin = project.plugins.getPlugin(LibraryPlugin::class.java) as LibraryPlugin
            //cannot got jar file input in library Transform, so got them by variantManager
            libPlugin.variantManager.variantScopes.forEach {
                if (it.fullVariantName != variantName) {
                    return@forEach
                }
//                it.getArtifactCollection(
//                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
//                        AndroidArtifacts.ArtifactScope.EXTERNAL,
//                        AndroidArtifacts.ArtifactType.CLASSES)
//                        .artifactFiles.files
//                        .forEach { file ->
//                            //here we can get all jar file for dependencies
//                        }
                val task = it.taskContainer.javacTask.get()
                task.classpath.files.forEach { file ->
                    InjectHelper.instance.appendClassPath(file.absolutePath)
                }
            }
        } else {
            checkDuplicate = project.componentExtension().buildOption.checkPathDuplicate
            if (!checkDuplicate) {
                return
            }
            //check path duplicate
            duplicateChecker = DuplicateChecker()
            val extension = project.extensions.getByName("android") as BaseExtension
            val jarPathList = arrayListOf<URL>()
            transformInvocation.inputs.forEach { input ->
                input.jarInputs.forEach {
                    jarPathList.add(URL("file://${it.file.absolutePath}"))
                }
            }
            jarPathList.add(URL("file://" + File(extension.sdkDirectory, "platforms/${extension.compileSdkVersion}/android.jar").absolutePath))
            baseClassLoader = URLClassLoader(jarPathList.toArray(arrayOf()), this::class.java.classLoader)
        }
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        if (!transformInvocation.isIncremental) {
            return
        }
        //initial incremental info, in here khala will load pre-build's status
        IncrementalHelper.loadPersistentField(this, transformInvocation.persistenceOutputDir())
        if (nodeSetByGroup.isNotEmpty()) {
            val removedClass = arrayListOf<String>()
            nodeSetByGroup.forEach {
                it.value.forEach setForEach@{ nodeInfo ->
                    if (nodeInfo.ctClass == CtClass.voidType) {
                        removedClass.add(nodeInfo.path)
                        return@setForEach
                    }
                }
            }
            removedClass.forEach {
                Log.d("path removed: $it")
                nodeSetByGroup.removeNode(it)
            }
        }
    }

    override fun onClassVisited(ctClass: CtClass): Boolean {
        return onIncrementalClassVisited(Status.ADDED, ctClass)
    }

    override fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean {
        //traversal all .class file and find the class which annotate by Router, record router path
        //and Class for RouterNode construction
        if (!ctClass.hasAnnotation(Router::class.java)) {
            return false
        }
        val routerAnnotation = ctClass.getAnnotation(Router::class.java) as Router
        val path = routerAnnotation.path
        val nodeInfo = NodeInfo(path, ctClass)
        if (status == Status.CHANGED && nodeSetByGroup.contains(nodeInfo)) {
            nodeSetByGroup.removeNode(nodeInfo)
        }
        nodeSetByGroup.putNode(nodeInfo)
        return insertInjectImplement(nodeInfo)
    }

    override fun onRemovedClassVisited(ctClass: CtClass) {
        if (!ctClass.hasAnnotation(Router::class.java)) {
            return
        }
        val routerAnnotation = ctClass.getAnnotation(Router::class.java) as Router
        val path = routerAnnotation.path
        val nodeInfo = NodeInfo(path, ctClass)
        nodeSetByGroup.removeNode(nodeInfo)
    }

    private val genClassSet = arrayListOf<String>()
    override fun onJarVisited(status: Status, jarInput: JarInput) {
        genClassSet.clear()
        //这里处理直接引用的一方lib
        if (jarInput.scopes.size == 1 && jarInput.scopes.contains(QualifiedContent.Scope.SUB_PROJECTS)) {
            val libJar = jarInput.file
            InjectHelper.instance.appendClassPath(libJar.absolutePath)
            ZipHelper.traversalZip(libJar) {
                if (!it.name.endsWith(".class")) {
                    return@traversalZip
                }
                val className = ZipHelper.entryNameToClassName(it.name)
                //这里状态要是CHANGED的，因为jar的状态是整个jar包的，里面的类单独状态不确定
                val ctClass = InjectHelper.instance.getClassPool()[className]
                onIncrementalClassVisited(Status.CHANGED, ctClass)
                ctClass.detach()
            }
            return
        }
        super.onJarVisited(status, jarInput)
        if (checkDuplicate && genClassSet.isNotEmpty()) {
            val classLoader = URLClassLoader(arrayOf(URL("file://${jarInput.file.absolutePath}")), baseClassLoader)

            genClassSet.forEach {
                val providerClass = classLoader.loadClass("${Constants.ROUTER_GEN_FILE_PACKAGE}$it")
                val nodeProvider = providerClass.newInstance() as IRouterNodeProvider
                nodeProvider.routerNodes.forEach { node ->
                    duplicateChecker?.check(node)
                }
            }
        }
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry,
                                   jarFile: File) {
        if (isComponent) {
            return
        }
        if (zipEntry.name.startsWith(Constants.ROUTER_GEN_FILE_FOLDER)) {
            Log.d("found router:${zipEntry.name}")
            libProviderConstants.putGroupedProviderWithEntryName(zipEntry.name)
            genClassSet.add(LibProviderContainer.getNameWithEntryName(zipEntry.name))
            return
        }
        if (zipEntry.name.startsWith(Constants.SERVICE_ALIAS_PROVIDER_FILE_FOLDER)) {
            Log.d("found service alias:${zipEntry.name}")
            libProviderConstants.putAliasProviderWithEntryName(zipEntry.name)
        }
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        IncrementalHelper.savePersistentField(this, transformInvocation.persistenceOutputDir())
        val dest = transformInvocation.outputProvider.getContentLocation(
                "routerGen",
                TransformManager.CONTENT_CLASS,
                TransformManager.PROJECT_ONLY,
                Format.DIRECTORY)
        if (dest.exists()) {
            FileUtils.deleteDirectory(dest)
        }
        if (checkDuplicate) {
            nodeSetByGroup.forEach { (_, set) ->
                set.forEach {
                    duplicateChecker?.check(it.path, it.ctClass.name)
                }
            }
        }
        if (nodeSetByGroup.isNotEmpty()) {
            nodeSetByGroup.forEach {
                genRouterProviderImpl(dest, it.key, it.value)
            }
        }
        //pair first: super interface's class full name, second: node path
        val componentServiceAlias = ServiceNodeAlias()
        nodeSetByGroup.forEach {
            it.value.forEach { nodeInfo ->
                if (nodeInfo.type == NodeType.COMPONENT_SERVICE) {
                    componentServiceAlias.putNodeInfo(nodeInfo)
                }
            }
        }

        if (isComponent && componentServiceAlias.isNotEmpty()) {
            genServiceAliasProviderImpl(dest, componentServiceAlias)
        }
        if (!isComponent) {
            nodeSetByGroup.forEach {
                libProviderConstants.putGroupedProvider(it.key, genRouterProviderClassName(it.key))
            }
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterLazyLoader::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(IRouterNodeProvider::class.java))
            InjectHelper.instance.getClassPool().appendClassPath(ClassClassPath(CollectionHelper::class.java))
            Log.d("generate lazy loader")
            genRouterLazyLoaderImpl(dest, libProviderConstants.groupedProviderMap,
                    componentServiceAlias, libProviderConstants.aliasProviderList)
        }
    }

    /**
     *
     * @return if return true, this class need writeFile()
     */
    private fun insertInjectImplement(nodeInfo: NodeInfo): Boolean {
        val injectInfoList = arrayListOf<InjectInfo>()
        val classPool = InjectHelper.instance.getClassPool()
        if (nodeInfo.type != NodeType.ACTIVITY && nodeInfo.type != NodeType.FRAGMENT) {
            //only inject for activity and fragment
            return false
        }

        val ctClass = nodeInfo.ctClass
        ctClass.declaredFields.forEach {
            var inject = it.getAnnotation(Inject::class.java) ?: return@forEach
            if (Modifier.isFinal(it.modifiers)) {
                Log.e("skip field ${ctClass.simpleName}.${it.name} inject: cannot inject value for final field.")
                return@forEach
            }
            inject = inject as Inject
            val annotationName = if (inject.name != "") {
                inject.name
            } else {
                it.name
            }
            injectInfoList.add(InjectInfo(it, annotationName, ctClass))
        }
        if (injectInfoList.size > 0) {
            val isFragment = nodeInfo.type == NodeType.FRAGMENT
            try {
                val method = ctClass.getDeclaredMethod("autoSynthetic\$FieldInjectKhala")
                method.setBody(produceInjectMethod(isFragment, injectInfoList))
            } catch (e: Exception) {
                ctClass.addInterface(classPool[Constants.INJECTABLE_CLASSNAME])
                ctClass.addMethod(genAutoSyntheticInjectMethod(isFragment, injectInfoList, ctClass))
            }
        }
        return injectInfoList.size > 0
    }

    private fun genAutoSyntheticInjectMethod(isFragment: Boolean, infoList: ArrayList<InjectInfo>, ctClass: CtClass): CtMethod {
        return CtMethod.make("public void autoSynthetic\$FieldInjectKhala()${produceInjectMethod(isFragment, infoList)}", ctClass)
    }

    private fun produceInjectMethod(isFragment: Boolean, infoList: ArrayList<InjectInfo>): String {
        val stringBuilder = StringBuilder("{")
        if (infoList.size > 0) {
            stringBuilder.append("if(!(\$0 instanceof ${Constants.INJECTABLE_CLASSNAME})){return;}")
            stringBuilder.append(
                    if (!isFragment) "android.content.Intent var = getIntent();"
                    else "android.os.Bundle var = getArguments();")
                    .append("if(var == null){return;}")
            infoList.forEach {
                stringBuilder.append("${it.fieldName} = ")
                        .append(getPrefixStatement(it))
                        .append(getExtraStatement(isFragment, it))
            }
        }
        stringBuilder.append("}")
        return stringBuilder.toString()
    }

    private fun getPrefixStatement(injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val classType = injectInfo.classType
        return when (InjectType.values()[type]) {
            InjectType.SERIALIZABLE, InjectType.PARCELABLE_ARRAY, InjectType.PARCELABLE -> "(${classType.name})var."
            else -> "var."
        }
    }

    private fun getExtraStatement(isFragment: Boolean, injectInfo: InjectInfo): String {
        val type = injectInfo.type
        val annotationName = injectInfo.annotationName
        val fieldName = injectInfo.fieldName
        return when (InjectType.values()[type]) {
            InjectType.INTEGER -> {
                if (isFragment) "getInt(\"$annotationName\", $fieldName);"
                else "getIntExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.INT_ARRAY -> {
                if (isFragment) "getIntArray(\"$annotationName\");"
                else "getIntArrayExtra(\"$annotationName\");"
            }
            InjectType.INT_LIST -> {
                if (isFragment) "getIntegerArrayList(\"$annotationName\");"
                else "getIntegerArrayListExtra(\"$annotationName\");"
            }
            InjectType.BOOLEAN -> {
                if (isFragment) "getBoolean(\"$annotationName\", $fieldName);"
                else "getBooleanExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.BOOLEAN_ARRAY -> {
                if (isFragment) "getBooleanArray(\"$annotationName\");"
                else "getBooleanArrayExtra(\"$annotationName\");"
            }
            InjectType.STRING -> {
                if (isFragment) "getString(\"$annotationName\");"
                else "getStringExtra(\"$annotationName\");"
            }
            InjectType.STRING_ARRAY -> {
                if (isFragment) "getStringArray(\"$annotationName\");"
                else "getStringArrayExtra(\"$annotationName\");"
            }
            InjectType.STRING_LIST -> {
                if (isFragment) "getStringArrayList(\"$annotationName\");"
                else "getStringArrayListExtra(\"$annotationName\");"
            }
            InjectType.CHARACTER -> {
                if (isFragment) "getChar(\"$annotationName\", $fieldName);"
                else "getCharExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.CHAR_ARRAY -> {
                if (isFragment) "getCharArray(\"$annotationName\");"
                else "getCharArrayExtra(\"$annotationName\");"
            }
            InjectType.SERIALIZABLE -> {
                if (isFragment) "getSerializable(\"$annotationName\");"
                else "getSerializableExtra(\"$annotationName\");"
            }
            InjectType.PARCELABLE -> {
                if (isFragment) "getParcelable(\"$annotationName\");"
                else "getParcelableExtra(\"$annotationName\");"
            }
            InjectType.PARCELABLE_ARRAY -> {
                if (isFragment) "getParcelableArray(\"$annotationName\");"
                else "getParcelableArrayExtra(\"$annotationName\");"
            }
            InjectType.PARCELABLE_LIST -> {
                if (isFragment) "getParcelableArrayList(\"$annotationName\");"
                else "getParcelableArrayListExtra(\"$annotationName\");"
            }
            InjectType.BYTE -> {
                if (isFragment) "getByte(\"$annotationName\", $fieldName);"
                else "getByteExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.BYTE_ARRAY -> {
                if (isFragment) "getByteArray(\"$annotationName\");"
                else "getByteArrayExtra(\"$annotationName\");"
            }
            InjectType.DOUBLE -> {
                if (isFragment) "getDouble(\"$annotationName\", $fieldName);"
                else "getDoubleExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.DOUBLE_ARRAY -> {
                if (isFragment) "getDoubleArray(\"$annotationName\");"
                else "getDoubleArrayExtra(\"$annotationName\");"
            }
            InjectType.FLOAT -> {
                if (isFragment) "getFloat(\"$annotationName\", $fieldName);"
                else "getFloatExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.FLOAT_ARRAY -> {
                if (isFragment) "getFloatArray(\"$annotationName\");"
                else "getFloatArrayExtra(\"$annotationName\");"
            }
            InjectType.LONG -> {
                if (isFragment) "getLong(\"$annotationName\", $fieldName);"
                else "getLongExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.LONG_ARRAY -> {
                if (isFragment) "getLongArray(\"$annotationName\");"
                else "getLongArrayExtra(\"$annotationName\");"
            }
            InjectType.SHORT -> {
                if (isFragment) "getShort(\"$annotationName\", $fieldName);"
                else "getShortExtra(\"$annotationName\", $fieldName);"
            }
            InjectType.SHORT_ARRAY -> {
                if (isFragment) "getShortArray(\"$annotationName\");"
                else "getShortArrayExtra(\"$annotationName\");"
            }
            InjectType.CHAR_SEQUENCE -> {
                if (isFragment) "getCharSequence(\"$annotationName\");"
                else "getCharSequenceExtra(\"$annotationName\");"
            }
            InjectType.CHAR_SEQUENCE_ARRAY -> {
                if (isFragment) "getCharSequenceArray(\"$annotationName\");"
                else "getCharSequenceArrayExtra(\"$annotationName\");"
            }
            InjectType.CHAR_SEQUENCE_LIST -> {
                if (isFragment) "getCharSequenceArrayList(\"$annotationName\");"
                else "getCharSequenceArrayListExtra(\"$annotationName\");"
            }
        }
    }

    private fun genRouterProviderImpl(dir: File, group: String, nodeList: Set<NodeInfo>) {
        MethodGen(Constants.ROUTER_GEN_FILE_PACKAGE + genRouterProviderClassName(group))
                .interfaces(IRouterNodeProvider::class.java)
                .signature(returnStatement = "public java.util.List",
                        name = "getRouterNodes")
                .body { produceNodesMethodBodySrc(nodeList) }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun produceNodesMethodBodySrc(nodeList: Set<NodeInfo>): String {
        val builder = StringBuilder("{")
                .append("java.util.ArrayList list = new java.util.ArrayList();")
        nodeList.forEach {
            val typeStr = "${Constants.NODE_TYPE_CLASSNAME}.${it.type}"
            builder.append("list.add(${Constants.NODE_FACTORY_CLASSNAME}.produceRouterNode($typeStr, \"${it.path}\", ${it.ctClass.name}.class));")
        }
        builder.append("return list;")
                .append("}")
        return builder.toString()
    }

    private fun genServiceAliasProviderImpl(dir: File, componentServiceAlias: ServiceNodeAlias) {
        val name = Constants.SERVICE_ALIAS_PROVIDER_FILE_PACKAGE + genServiceAliasProviderClassName()
        MethodGen(name)
                .interfaces(IServiceAliasProvider::class.java)
                .signature("public android.util.SparseArray",
                        "getServiceAlias")
                .body { produceAliasProviderBodySrc(componentServiceAlias) }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun produceAliasProviderBodySrc(alias: ServiceNodeAlias): String {
        val sb = StringBuilder("{")
                .append("android.util.SparseArray result = new android.util.SparseArray();")
        alias.forEach { interfaceName, routerPath ->
            val hash = interfaceName.hashCode()
            sb.append("result.put($hash, \"${routerPath}\");")
        }
        sb.append("return result;}")
        return sb.toString()
    }

    private fun genRouterLazyLoaderImpl(dir: File,
                                        groupMap: MutableMap<String, ArrayList<String>>,
                                        aliasInfoList: ServiceNodeAlias,
                                        aliasProviderList: ArrayList<String>) {
        val name = Constants.ROUTER_GEN_FILE_PACKAGE + Constants.LAZY_LOADER_IMPL_NAME
        MethodGen(name)
                .interfaces(IRouterLazyLoader::class.java)
                .signature("public java.util.List",
                        "lazyLoadFactoryByGroup",
                        "(String group)")
                .body { produceLazyLoadFactoryBodySrc(groupMap) }
                .gen()
        MethodGen(name)
                .signature("public android.util.SparseArray",
                        "loadServiceAliasMap")
                .body { produceLoadServiceAliasMapMethodBodySrc(aliasInfoList, aliasProviderList) }
                .gen()?.writeFile(dir.absolutePath)
    }

    private fun produceLazyLoadFactoryBodySrc(groupMap: MutableMap<String, ArrayList<String>>): String {
        val sb = StringBuilder("{")
                .append("java.util.ArrayList result =")
                .appendln("new java.util.ArrayList();")
        if (groupMap.isNotEmpty()) {
            sb.append("switch(\$1.hashCode()) {")
            groupMap.forEach {
                val group = it.key
                sb.append("case ${group.hashCode()}:{")
                it.value.forEach { name ->
                    sb.append("result.add(new ${Constants.ROUTER_GEN_FILE_PACKAGE}$name());")
                }
                sb.append("return result;}")
            }
            sb.append("default:break;}")
        }
        sb.append("return result;}")
        return sb.toString()
    }

    private fun produceLoadServiceAliasMapMethodBodySrc(aliasInfoList: ServiceNodeAlias,
                                                        aliasProviderList: ArrayList<String>): String {
        val sb = StringBuilder("{")
                .append("android.util.SparseArray result = new android.util.SparseArray();")
        aliasInfoList.forEach { interfaceName, routerPath ->
            val hash = interfaceName.hashCode()
            sb.append("result.put($hash, \"${routerPath}\");")
        }
        aliasProviderList.forEach {
            sb.append("io.nebula.platform.khala.util.CollectionHelper.putAllSparseArray(result, new ${Constants.SERVICE_ALIAS_PROVIDER_FILE_PACKAGE}$it().getServiceAlias());")
        }
        sb.append("return result;}")
        return sb.toString()
    }

    private fun genRouterProviderClassName(group: String): String {
        val componentName = getComponentExtension().componentName?.replace('-', '_')
        return "$group\$$componentName\$NodeProvider"
    }

    private fun genServiceAliasProviderClassName(): String {
        val componentName = getComponentExtension().componentName?.replace('-', '_')
        return "$componentName\$ServiceAliasProvider"
    }

    private fun getComponentExtension(): ComponentExtension {
        return project.extensions.findByType(ComponentExtension::class.java)
                ?: throw GradleException("can not find ComponentExtension, please check your build.gradle file")
    }


    /**
     * save information of nodeInfo split by group, these data will used of generate RouterNodeProviderImpl
     */
    private class NodeInfoSetGroupSplit {
        private val nodeSetByGroup = mutableMapOf<String, HashSet<NodeInfo>>()

        fun putNode(nodeInfo: NodeInfo) {
            val set = nodeSetByGroup.computeIfAbsent(getGroupWithPath(nodeInfo.path, nodeInfo.ctClass.name)) {
                hashSetOf()
            }
            val result = set.add(nodeInfo)
            if (!result) {
                val dup = set.find { it.path == nodeInfo.path }
                throw RouterPathDuplicateException(nodeInfo.path, nodeInfo.ctClass.name,
                        dup!!.path, dup.ctClass.name)
            }
        }

        fun contains(nodeInfo: NodeInfo): Boolean {
            val group = getGroupWithPath(nodeInfo.path, nodeInfo.ctClass.name)
            val set = nodeSetByGroup[group] ?: return false
            return set.contains(nodeInfo)
        }

        fun removeNode(nodeInfo: NodeInfo) {
            val group = getGroupWithPath(nodeInfo.path, nodeInfo.ctClass.name)
            val set = nodeSetByGroup[group] ?: return
            set.remove(nodeInfo)
            if (set.size == 0) {
                nodeSetByGroup.remove(group)
            }
        }

        fun removeNode(path: String) {
            val group = getGroupWithPath(path, null)
            val set = nodeSetByGroup[group] ?: return
            set.remove(NodeInfo(path, CtClass.voidType))
            if (set.size == 0) {
                nodeSetByGroup.remove(group)
            }
        }

        fun isNotEmpty(): Boolean {
            return nodeSetByGroup.isNotEmpty()
        }

        fun forEach(action: (Map.Entry<String, HashSet<NodeInfo>>) -> Unit) {
            nodeSetByGroup.forEach(action)
        }

        private fun getGroupWithPath(path: String, clazz: String?): String {
            val strings = path.split('/')
            if (strings.size < 3) {
                throw GradleException("invalid router path: \"$path\" in $clazz. Router Path must starts with '/' and has a group segment")
            }
            return strings[1]
        }
    }

    /**
     * contains all provider classes in this class
     */
    private class LibProviderContainer {
        val aliasProviderList = arrayListOf<String>()
        val groupedProviderMap = mutableMapOf<String, ArrayList<String>>()

        fun putAliasProviderWithEntryName(entryName: String) {
            aliasProviderList.add(getNameWithEntryName(entryName))
        }

        fun putGroupedProviderWithEntryName(entryName: String) {
            val className = getNameWithEntryName(entryName)
            val group = getGroupWithEntryName(entryName)
            putGroupedProvider(group, className)
        }

        fun putGroupedProvider(group: String, className: String) {
            groupedProviderMap.computeIfAbsent(group) {
                arrayListOf()
            }.add(className)
        }

        private fun getGroupWithEntryName(name: String): String {
            return name.split('/').last().split('$')[0]
        }

        companion object {
            fun getNameWithEntryName(name: String): String {
                return name.split('/').last().split('.')[0]
            }
        }
    }


    /**
     * save node path and node correspond ctClass, this class can get [NodeType]
     */
    private data class NodeInfo(val path: String,
                                val ctClass: CtClass) {
        companion object {
            private val typeCache = mutableMapOf<CtClass, NodeType>()
        }

        /**
         * nodeType, mark current node type for different node subClass instantiate
         */
        val type = getNodeType(ctClass)

        private fun getNodeType(ctClass: CtClass): NodeType {
            if (ctClass.isPrimitive) {
                return NodeType.UNSPECIFIED
            }
            return typeCache.computeIfAbsent(ctClass) {
                val classPool = InjectHelper.instance.getClassPool()
                NodeType.values().forEach { nodeType ->
                    nodeType.supportClasses().forEach support@{
                        if (it == "") {
                            return@support
                        }
                        try {
                            val supportClass = classPool[it]
                            if (ctClass.subtypeOf(supportClass)) {
                                return@computeIfAbsent nodeType
                            }
                        } catch (e: Exception) {
                            Log.w("cannot got $it in ${ctClass.name} when check node type. Error msg: ${e.javaClass}:${e.message}")
                            if (Log.level > 3) {
                                Log.w("stacktrace:")
                                e.printStackTrace()
                            }
                        }
                    }
                }
                return@computeIfAbsent NodeType.UNSPECIFIED
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other is NodeInfo && other.path == path) {
                return true
            }
            return super.equals(other)
        }

        override fun hashCode(): Int {
            return path.hashCode()
        }
    }

    /**
     * component service alias, map of class name and router path
     * //todo: router path change to Set
     */
    private class ServiceNodeAlias {
        private val map = mutableMapOf<String, String>()

        fun putNodeAlias(interfaceName: String, routerPath: String) {
            map[interfaceName] = routerPath
        }

        fun putNodeInfo(nodeInfo: NodeInfo) {
            nodeInfo.ctClass.interfaces.forEach {
                putNodeAlias(it.name, nodeInfo.path)
            }
        }

        fun forEach(action: (interfaceName: String, routerPath: String) -> Unit) {
            map.forEach(action)
        }

        fun isNotEmpty(): Boolean {
            return map.isNotEmpty()
        }
    }

    /**
     * 保存注入逻辑的参数，提供一些注入流程中的工具方法简化操作
     */
    private data class InjectInfo(val ctField: CtField,
                                  val annotationName: String,
                                  private val ctClass: CtClass) {
        companion object {
            private val classPool = InjectHelper.instance.getClassPool()
            private val serializableType = classPool["java.io.Serializable"]
            private val parcelableType = classPool["android.os.Parcelable"]
            private val charSequenceType = classPool["java.lang.CharSequence"]
        }

        var type: Int = 0
        val fieldName: String = ctField.name
        val classType: CtClass = ctField.type

        init {
            type = initType()
        }

        /**
         * initType() method will decide and check the type of the field which transport by bundle
         */
        private fun initType(): Int {
            return when (classType.name) {
                "int", "java.lang.Integer" -> InjectType.INTEGER.ordinal
                "boolean", "java.lang.Boolean" -> InjectType.BOOLEAN.ordinal
                "java.lang.String" -> InjectType.STRING.ordinal
                "char", "java.lang.Character" -> InjectType.CHARACTER.ordinal
                "float", "java.lang.Float" -> InjectType.FLOAT.ordinal
                "double", "java.lang.Double" -> InjectType.DOUBLE.ordinal
                "byte", "java.lang.Byte" -> InjectType.BYTE.ordinal
                "long", "java.lang.Long" -> InjectType.LONG.ordinal
                "short", "java.lang.Short" -> InjectType.SHORT.ordinal

                //keep these for abstract type field
                "java.io.Serializable" -> InjectType.SERIALIZABLE.ordinal
                "android.os.Parcelable" -> InjectType.PARCELABLE.ordinal
                "java.lang.CharSequence" -> InjectType.CHAR_SEQUENCE.ordinal

                "int[]" -> InjectType.INT_ARRAY.ordinal
                "boolean[]" -> InjectType.BOOLEAN_ARRAY.ordinal
                "java.lang.String[]" -> InjectType.STRING_ARRAY.ordinal
                "char[]" -> InjectType.CHAR_ARRAY.ordinal
                "float[]" -> InjectType.FLOAT_ARRAY.ordinal
                "double[]" -> InjectType.DOUBLE_ARRAY.ordinal
                "byte[]" -> InjectType.BYTE_ARRAY.ordinal
                "long[]" -> InjectType.LONG_ARRAY.ordinal
                "short[]" -> InjectType.SHORT_ARRAY.ordinal
                "java.lang.CharSequence[]" -> InjectType.CHAR_SEQUENCE_ARRAY.ordinal
                "android.os.Parcelable[]" -> InjectType.PARCELABLE_ARRAY.ordinal

                //process put[...]ArrayList type
                "java.util.ArrayList" -> {
                    val fieldInfo = ctClass.classFile.fields.find { it is FieldInfo && it.name == fieldName } as FieldInfo
                    val sa = fieldInfo.getAttribute(SignatureAttribute.tag) as SignatureAttribute
                    //do this for get generic type int ArrayList,
                    //check this type whether fit for bundle support type
                    val type = SignatureAttribute.toFieldSignature(sa.signature).jvmTypeName()
                    return when (val genericType = extractGeneric(type)) {
                        "java.lang.String" -> InjectType.STRING_LIST.ordinal
                        "java.lang.Integer" -> InjectType.INT_LIST.ordinal
                        //keep this for abstract
                        "java.lang.CharSequence" -> InjectType.CHAR_SEQUENCE_LIST.ordinal
                        else -> {
                            val ctClass = classPool[genericType]
                            when {
                                ctClass.subtypeOf(parcelableType) -> InjectType.PARCELABLE_LIST.ordinal
                                ctClass.subtypeOf(charSequenceType) -> InjectType.CHAR_SEQUENCE_LIST.ordinal
                                else -> throw InjectTypeException(ctClass.name, fieldName, type)
                            }
                        }
                    }
                }
                else -> {
                    when {
                        //process put[...]Array type
                        classType.isArray && classType.componentType.subtypeOf(parcelableType) -> InjectType.PARCELABLE_ARRAY.ordinal
                        classType.isArray && classType.componentType.subtypeOf(charSequenceType) -> InjectType.CHAR_SEQUENCE_ARRAY.ordinal
                        //process putParcelable/putSerializable type
                        classType.subtypeOf(parcelableType) -> InjectType.PARCELABLE.ordinal
                        classType.subtypeOf(serializableType) -> InjectType.SERIALIZABLE.ordinal
                        else -> throw InjectTypeException(ctClass.name, fieldName, classType.name)
                    }
                }
            }
        }

        private fun extractGeneric(s: String): String {
            val startIndex = s.indexOf('<') + 1
            val endIndex = s.lastIndexOf('>')
            return s.substring(startIndex, endIndex).trim()
        }
    }

    private class DuplicateChecker {
        data class NodeCheckInfo(val path: String, val className: String) {
            override fun equals(other: Any?): Boolean {
                if (other is NodeCheckInfo) {
                    return path == other.path
                }
                return super.equals(other)
            }

            override fun hashCode(): Int {
                return path.hashCode()
            }
        }

        private val checkSet = hashSetOf<NodeCheckInfo>()

        fun check(node: RouterNode) {
            checkInternal(node.path, node.target.name)
        }

        fun check(path: String, className: String) {
            checkInternal(path, className)
        }

        private fun checkInternal(path: String, className: String) {
            val result = checkSet.add(NodeCheckInfo(path, className))
            if (!result) {
                val dup = checkSet.find { it.path == path }
                throw RouterPathDuplicateException(path, className,
                        dup!!.path, dup.className)
            }
        }
    }
}