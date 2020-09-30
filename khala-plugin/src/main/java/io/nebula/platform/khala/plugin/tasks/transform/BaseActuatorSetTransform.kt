package io.nebula.platform.khala.plugin.tasks.transform

import io.nebula.platform.khala.plugin.utils.InjectHelper
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-11-18 18:09
 */
abstract class BaseActuatorSetTransform : BaseTraversalTransform() {
    private var actuatorSet: Set<TransformActuator> = emptySet()

    override fun preTraversal(transformInvocation: TransformInvocation) {
        actuatorSet = getTransformActuatorSet()
        actuatorSet.forEach {
            it.preTraversal(transformInvocation)
        }
    }

    override fun onChangedFile(status: Status, inputFile: File, outputDir: File, destFile: File): Boolean {
        val ctClass = getCtClassByFile(outputDir, destFile)
        var modify = false
        actuatorSet.forEach {
            modify = modify or it.onIncrementalClassVisited(status, ctClass)
        }
        if (modify) {
            ctClass.writeFile(outputDir.absolutePath)
            return true
        }
        return false
    }

    override fun onRemovedFileTransform(outputDir: File, destFile: File) {
//        val pathLength = destFile.absolutePath.length
//        val className = destFile.absolutePath
//                .subSequence(outputDir.absolutePath.length + 1, pathLength - 6)
//                .toString().replace('/', '.')
//
//        val classPool = InjectHelper.instance.getClassPool()
//        val classPath = classPool.appendClassPath(outputDir.absolutePath)
//        val ctClass = classPool[className]
//        actuatorSet.forEach {
//            it.onRemovedClassVisited(ctClass)
//        }
//        ctClass.detach()
//        classPool.removeClassPath(classPath)
    }

    override fun onInputFileVisited(ctClass: CtClass, outputDir: File): Boolean {
        var modify = false
        actuatorSet.forEach {
            modify = modify or it.onClassVisited(ctClass)
        }
        if (modify) {
            ctClass.writeFile(outputDir.absolutePath)
            return true
        }
        return false
    }

    override fun onJarVisited(status: Status, jarInput: JarInput): Boolean {
        actuatorSet.forEach {
            it.onJarVisited(status, jarInput)
        }
        return false
    }

    override fun postTransform(transformInvocation: TransformInvocation) {
        actuatorSet.forEach {
            it.postTransform(transformInvocation)
        }
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        actuatorSet.forEach {
            it.preTransform(transformInvocation)
        }
    }

    abstract fun getTransformActuatorSet(): Set<TransformActuator>

    private fun getCtClassByFile(outputDir: File, file: File): CtClass {
        val pathLength = file.absolutePath.length
        val className = file.absolutePath
                .subSequence(outputDir.absolutePath.length + 1, pathLength - 6)
                .toString().replace('/', '.')
        return InjectHelper.instance.getClassPool()[className]
    }
}