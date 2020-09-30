package io.nebula.platform.khala.plugin.tasks.transform

import io.nebula.platform.khala.plugin.resolve.PrefixHelper
import io.nebula.platform.khala.plugin.utils.InjectHelper
import io.nebula.platform.khala.plugin.utils.Log
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.scope.InternalArtifactType
import javassist.CtClass
import javassist.CtField
import javassist.expr.ExprEditor
import javassist.expr.FieldAccess
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import java.io.File
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-11-19 11:05
 */
class PrefixRActuator(private val project: Project,
                      isComponent: Boolean) : TypeActuator(isComponent) {
    private var applicationId = ""
    private var prefix: String? = null

    override fun preTraversal(transformInvocation: TransformInvocation) {
        prefix = PrefixHelper.instance.prefix
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        val p = project
        val variantName = transformInvocation.context.variantName
        val libPlugin = p.plugins.getPlugin(LibraryPlugin::class.java) as LibraryPlugin
        (libPlugin.extension as LibraryExtension).libraryVariants.all {
            if (it.name == variantName) {
                applicationId = it.applicationId
                Log.d("applicationId:$applicationId")
            }
        }
        libPlugin.variantManager.variantScopes.forEach {
            if (it.fullVariantName != variantName) {
                return@forEach
            }
            val provider = it.artifacts.getFinalProduct<FileSystemLocation>(InternalArtifactType.COMPILE_ONLY_NOT_NAMESPACED_R_CLASS_JAR)
            InjectHelper.instance.appendClassPath(provider.get().asFile.absolutePath)
        }
        val rCtClass = InjectHelper.instance.getClassPool()["$applicationId.R"]
        prefixCustomCtClassField(rCtClass)
    }

    override fun onClassVisited(ctClass: CtClass): Boolean {
        return onIncrementalClassVisited(Status.ADDED, ctClass)
    }

    override fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean {
        return prefixRClassFieldAccess(ctClass, applicationId)
    }

    override fun onRemovedClassVisited(ctClass: CtClass) {
    }

    override fun onJarEntryVisited(zipEntry: ZipEntry, jarFile: File) {
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
    }

    private fun prefixCustomCtClassField(ctClass: CtClass) {
        Log.d("prefix R.class field access. which class is: ${ctClass.name}")
        val classInfo = arrayListOf<Pair<String, MutableList<String>>>()
        ctClass.nestedClasses.forEach {
            val pair = Pair(it.name, arrayListOf<String>())

            it.fields.forEach { ctField ->
                if (it.isFrozen) {
                    it.defrost()
                }
                //eg:it.simpleName = "R$id"
                if (PrefixHelper.instance.isRefNeedPrefix(it.simpleName.substring(2), ctField.name)) {
                    pair.second.add("public static ${ctField.type.name} $prefix${ctField.name};")
                } else {
                    pair.second.add("public static ${ctField.type.name} ${ctField.name};")
                }
            }
            classInfo.add(pair)
        }
        ctClass.detach()
        /* with gradle 3.3.0, R file changed from .java file to .jar file, if directly use
         * fieldAccess replace, new access while inline by constant, so construct new ctclass
         * in javassist, it will be correct behavior
         */
        classInfo.forEach {
            val newRClass = InjectHelper.instance.getClassPool().makeClass(it.first)
            it.second.forEach { field ->
                newRClass.addField(CtField.make(field, newRClass))
            }
        }
    }

    private fun prefixRClassFieldAccess(ctClass: CtClass, applicationId: String): Boolean {
        if (prefix == null) {
            return false
        }
        if (isRFile(ctClass.simpleName)) {
            //skip R.class's field access prefix
            return false
        }
        if (ctClass.isFrozen) {
            ctClass.defrost()
        }
        var modify = false
        ctClass.instrument(object : ExprEditor() {
            override fun edit(f: FieldAccess?) {
                if (f == null) {
                    return
                }
                if (f.isReader && needPrefix(f.className, f.fieldName, applicationId)) {
                    Log.d("{\$_ = ${f.className}.$prefix${f.fieldName};}")
                    f.replace("{\$_ = ${f.className}.$prefix${f.fieldName};}")
                    modify = true
                }
            }
        })
        return modify
    }

    private fun isRFile(name: String): Boolean = name == "R" || name.startsWith("R$")

    private fun needPrefix(fullName: String, ref: String, applicationId: String): Boolean {
        if (!isCustomRFile(fullName, applicationId)) {
            return false
        }
        val strings = fullName.split('$')
        if (strings.size <= 1) {
            return false
        }
        val rName = strings[1]
        return PrefixHelper.instance.isRefNeedPrefix(rName, ref)
    }

    private fun isCustomRFile(name: String, applicationId: String) = name.startsWith("$applicationId.R")

}