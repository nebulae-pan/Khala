package io.nebula.platform.khala.plugin.utils

import io.nebula.platform.khala.plugin.extesion.ComponentExtension
import com.android.build.api.transform.Format
import com.android.build.api.transform.TransformInvocation
import org.gradle.api.Project
import java.io.File

/**
 * Created by nebula on 2019-12-25
 */
var componentExtension: ComponentExtension? = null

fun Project.componentExtension(): ComponentExtension {
    //这里componentExtension不能缓存，每次获取新的，否则在build.gradle修改配置拿不到新的
    componentExtension = extensions.getByType(ComponentExtension::class.java)
    return componentExtension!!
}

fun TransformInvocation.persistenceOutputDir(): File {
    inputs.forEach {
        it.jarInputs.forEach { jarInput ->
            return outputProvider.getContentLocation(jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes, Format.JAR).parentFile
        }
        it.directoryInputs.forEach { dirInput ->
            return outputProvider.getContentLocation(dirInput.name,
                    dirInput.contentTypes,
                    dirInput.scopes, Format.DIRECTORY).parentFile
        }
    }
    throw RuntimeException("create persistence output directory error, no input of transformInvocation detected.")
}