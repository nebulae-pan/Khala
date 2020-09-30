package io.nebula.platform.khala.plugin.tasks.transform

import com.android.build.api.transform.JarInput
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import javassist.CtClass
import java.io.File

/**
 * Transform的一个抽象。本身transform之间传递结果需要通过I/O，效率较低，在这里把所有插件内的transform通过Actuator
 * 处理，全部放到一个Transform中
 *
 * @author panxinghai
 *
 * date : 2019-11-18 16:53
 */
interface TransformActuator {
    /**
     * see [BaseTraversalTransform.preTraversal]
     */
    fun preTraversal(transformInvocation: TransformInvocation)

    /**
     * see [BaseTraversalTransform.preTransform]
     */
    fun preTransform(transformInvocation: TransformInvocation)

    /**
     * @return if true, this ctClass was be modified, actuator will write it to file, otherwise actuator
     * will directly copy source file to destination directory.
     */
    fun onClassVisited(ctClass: CtClass): Boolean

    fun onIncrementalClassVisited(status: Status, ctClass: CtClass): Boolean

    fun onRemovedClassVisited(ctClass: CtClass)

    fun onJarVisited(status: Status, jarInput: JarInput)

    /**
     * see [BaseTraversalTransform.postTransform]
     */
    fun postTransform(transformInvocation: TransformInvocation)
}