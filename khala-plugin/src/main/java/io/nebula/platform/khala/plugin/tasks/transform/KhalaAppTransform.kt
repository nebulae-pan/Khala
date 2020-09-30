package io.nebula.platform.khala.plugin.tasks.transform

import com.android.build.api.transform.TransformInvocation
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-10-14 22:35
 */
class KhalaAppTransform(private val project: Project) : BaseActuatorSetTransform() {
    override fun getTransformActuatorSet(): Set<TransformActuator> {
        return ImmutableSet.of(
                RouterCompileActuator(project, false)
                //需要代理初始化的话就把这句注释打开
//                , InitTaskCompileActuator(project, false)
        )
    }

    override fun preTransform(transformInvocation: TransformInvocation) {
        clearJarFactoryCache()
        super.preTransform(transformInvocation)
    }

    private fun clearJarFactoryCache() {
        val clazz = Class.forName("sun.net.www.protocol.jar.JarFileFactory")
        val fileCacheField = clazz.getDeclaredField("fileCache")
        val urlCacheField = clazz.getDeclaredField("urlCache")
        fileCacheField.isAccessible = true
        urlCacheField.isAccessible = true
        val fileCache = fileCacheField.get(null) as MutableMap<*, *>
        val urlCache = urlCacheField.get(null) as MutableMap<*, *>
        fileCache.clear()
        urlCache.clear()
    }

    override fun getName(): String {
        return "khalaApp"
    }
}