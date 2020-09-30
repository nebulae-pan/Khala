package io.nebula.platform.khala.plugin.tasks.transform

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import java.io.File

/**
 * @author panxinghai
 *
 * date : 2019-07-19 18:10
 */
abstract class BaseTransform : Transform() {

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_JARS
    }

    override fun isIncremental(): Boolean {
        return true
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    protected fun outputFiles(provider: TransformOutputProvider, directoryInput: DirectoryInput) {
        val dest = provider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
        // 将input的目录复制到output指定目录
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    protected fun getOutputDir(provider: TransformOutputProvider, directoryInput: DirectoryInput): File {
        return provider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
    }

    protected fun getOutputJar(provider: TransformOutputProvider, jarInput: JarInput): File {
        return provider.getContentLocation(
                jarInput.name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
        )
    }


    protected fun outputJarFile(provider: TransformOutputProvider, jarInput: JarInput) {
        val dest = provider.getContentLocation(
                jarInput.name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
        )
        // 将input的目录复制到output指定目录
        FileUtils.copyFile(jarInput.file, dest)
    }

}