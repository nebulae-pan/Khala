package io.nebula.platform.khala.plugin.tasks

import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import io.nebula.platform.khala.IComponentService
import io.nebula.platform.khala.annotation.ClassExposed
import io.nebula.platform.khala.plugin.utils.InjectHelper
import javassist.CtClass
import javassist.bytecode.FieldInfo
import javassist.bytecode.MethodInfo
import javassist.bytecode.SignatureAttribute
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

//fun main() {
//    val text = "java.util.List<Gender, AsyncTask<A,B,C>,Triple<TestA, TestB, TestC<Pair<TypeA, TypeB>>>>"
//    GenerateInterfaceArtifact.extractGenericClass(text).forEach {
//        println(it)
//    }
//}

/**
 * 根据[IComponentService]和[ClassExposed]判定需要暴露给外部调用的类。会根据需要暴露的Class做DFS找到所有被引用的
 * 类，一并打包，最终生成interface.jar。目前引用interface.jar是通过gradle的implementation 的@jar来做的，
 * interface.jar和实际的aar实际上是在一个路径下的两个文件，可能会有一定程度耦合，如果需要两个部分可以自定义决定依赖，
 * 可以修改上传逻辑和interfaceApi引用的方式。比如上传时上传至不同的仓库路径，比如原本逻辑上传仓库为com.google.android，
 * 那么interface.jar可以上传至com.google.android-api，引用时去指定路径拿就可以了，这样可以单独依赖或取消某一个代码包。
 *
 * Created by nebula on 2019-09-03
 */
open class GenerateInterfaceArtifact : AndroidVariantTask() {
    var javaSourceDir: File? = null
    var kotlinSourceDir: File? = null
    var destDir: File? = null

    private val refSet = hashSetOf<String>()

    private val ignorePackage = listOf("java.", "android.")

    @TaskAction
    fun taskAction() {
        if (javaSourceDir == null && kotlinSourceDir == null) {
            return
        }
        InjectHelper.instance.refresh()
        javaSourceDir?.let {
            InjectHelper.instance.appendClassPath(it.absolutePath)
            extractClass(it)
        }
        kotlinSourceDir?.let {
            InjectHelper.instance.appendClassPath(it.absolutePath)
            extractClass(it)
        }
        destDir?.deleteRecursively()
        val destFile = File(destDir, "interface.jar")
        destDir?.mkdirs()
        destFile.createNewFile()

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            refSet.forEach { ref ->
                val fileName = ref.replace('.', '/') + ".class"
                var file = File(javaSourceDir, fileName)
                if (!file.exists()) {
                    file = File(kotlinSourceDir, fileName)
                    if (!file.exists()) {
                        return@forEach
                    }
                }
                FileInputStream(file).use { fis ->
                    zos.putNextEntry(ZipEntry(fileName))

                    val buf1 = ByteArray(1024)
                    var len: Int
                    while (fis.read(buf1).also { len = it } > 0) {
                        zos.write(buf1, 0, len)
                    }
                }
            }
        }
    }

    /**
     * 提取目标目录下被ClassExposed标注和继承自IComponentService接口的类处理
     * @param source 目标目录
     * @see ClassExposed
     * @see IComponentService
     */
    private fun extractClass(source: File) {
        InjectHelper.instance.processFiles(source)
                .nameFilter { file -> file.name.endsWith(".class") }
                .classFilter { ctClass ->
                    ctClass.classFile2.interfaces.contains(IComponentService::class.java.name) or
                            ctClass.hasAnnotation(ClassExposed::class.java)
                }.forEach { ctClass, _ ->
                    refSet.add(ctClass.name)
                    retrieveRefClass(ctClass)
                }
    }

    /**
     * retrieve all reference Class of ComponentService's subClass, then put them into refSet
     * @param ctClass class need retrieve reference
     */
    private fun retrieveRefClass(ctClass: CtClass) {
        val classPool = InjectHelper.instance.getClassPool()
        //do follow for generic
        ctClass.classFile2.fields.forEach {
            val field = it as FieldInfo
            var ai = field.getAttribute(SignatureAttribute.tag)
            if (ai != null) {
                ai = ai as SignatureAttribute
                val objectType = SignatureAttribute.toFieldSignature(ai.signature)
                handleGenericRef(objectType.jvmTypeName(), ctClass)
            }
        }

        ctClass.classFile2.methods.forEach {
            val method = it as MethodInfo
            var ai = method.getAttribute(SignatureAttribute.tag)
            if (ai != null) {
                ai = ai as SignatureAttribute
                val methodAttributeInfo = SignatureAttribute.toMethodSignature((ai as SignatureAttribute).signature)
                handleGenericRef(methodAttributeInfo.returnType.jvmTypeName(), ctClass)
                methodAttributeInfo.typeParameters.forEach { type ->
                    handleGenericRef(type.name, ctClass)
                }
                methodAttributeInfo.parameterTypes.forEach { type ->
                    handleGenericRef(type.jvmTypeName(), ctClass)
                }
                methodAttributeInfo.exceptionTypes.forEach { type ->
                    handleGenericRef(type.jvmTypeName(), ctClass)
                }
            }
        }

        ctClass.refClasses.forEach { ref ->
            if (ref !is String) {
                return
            }
            if (isSuitableRef(ref, ctClass)) {
                refSet.add(ref)
                val refClass = classPool.getOrNull(ref) ?: return
                retrieveRefClass(refClass)
            }
        }
    }

    private fun isSuitableRef(ref: String, ctClass: CtClass): Boolean {
        return ref != ctClass.name
                && ref != IComponentService::class.java.name
                && !needIgnore(ref)
                && !refSet.contains(ref)
    }

    private fun needIgnore(name: String): Boolean {
        ignorePackage.forEach {
            if (name.startsWith(it)) {
                return true
            }
        }
        return false
    }

    private fun handleGenericRef(type: String, ctClass: CtClass) {
        extractGenericClass(type).forEach {
            if (isSuitableRef(it, ctClass)) {
                val refClass = InjectHelper.instance.getClassPool().getOrNull(it) ?: return@forEach
                if (refClass.isPrimitive) {
                    //primitive type needn't add
                    return@forEach
                }
                refSet.add(it)
                retrieveRefClass(refClass)
            }
        }
    }

    private fun extractGenericClass(type: String): HashSet<String> {
        val set = hashSetOf<String>()
        extractGenericInternal(type, set)
        return set
    }

    private fun extractGenericInternal(s: String, set: HashSet<String>) {
        val sub = subGenericString(s, set)
        if (sub == null) {
            set.add(s)
            return
        }
        val index = sub.indexOf(',')
        if (index > 0) {
            var gotLT = 0
            var lastIndex = 0
            for (i in sub.indices) {
                if (sub[i] == ',') {
                    if (gotLT == 0) {
                        extractGenericInternal(sub.substring(lastIndex, i).trim(), set)
                        lastIndex = i + 1
                    }//else skip
                    continue
                }
                if (sub[i] == '<') {
                    gotLT++
                    continue
                }
                if (sub[i] == '>') {
                    gotLT--
                }
                if (i == sub.length - 1) {
                    extractGenericInternal(sub.substring(lastIndex).trim(), set)
                }
            }

        } else {
            set.add(sub)
        }
    }

    private fun subGenericString(s: String, set: HashSet<String>): String? {
        val startIndex = s.indexOf('<') + 1
        val endIndex = s.lastIndexOf('>')
        if (startIndex <= 0 || endIndex < 0) {
            return null
        }
        set.add(s.substring(0, startIndex - 1))
        return s.substring(startIndex, endIndex).trim()
    }


    class ConfigAction(private val scope: VariantScope,
                       private val javaOutput: File?,
                       private val kotlinOutput: File?)
        : VariantTaskCreationAction<GenerateInterfaceArtifact>(scope) {
        override val name: String
            get() = scope.getTaskName("gen", "interfaceArtifact")
        override val type: Class<GenerateInterfaceArtifact>
            get() = GenerateInterfaceArtifact::class.java

        override fun configure(task: GenerateInterfaceArtifact) {
            super.configure(task)
            task.variantName = scope.fullVariantName
            task.javaSourceDir = javaOutput
            task.kotlinSourceDir = kotlinOutput
            task.destDir = FileUtils.join(
                    scope.globalScope.intermediatesDir,
                    "gen-interface-artifact",
                    scope.variantConfiguration.dirName)
        }
    }
}