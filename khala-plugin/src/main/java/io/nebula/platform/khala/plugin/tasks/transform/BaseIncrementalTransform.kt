package io.nebula.platform.khala.plugin.tasks.transform

import io.nebula.platform.khala.plugin.utils.Log
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.Status
import com.android.build.api.transform.TransformInvocation
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Base class for incremental compile transform.
 * @author panxinghai
 *
 * date : 2020-01-13 13:54
 */
abstract class BaseIncrementalTransform : BaseTransform() {
    override fun transform(transformInvocation: TransformInvocation?) {
        if (transformInvocation == null) {
            return
        }
        val isIncremental = transformInvocation.isIncremental
        val outputProvider = transformInvocation.outputProvider
        if (!isIncremental) {
            Log.p("is not incremental")
            outputProvider.deleteAll()
        }
        transformInvocation.inputs.forEach {
            it.jarInputs.forEach { jarInput ->
                val dest = getOutputJar(outputProvider, jarInput)
                when (jarInput.status) {
                    Status.ADDED, Status.CHANGED -> {
                        Log.p("jar ${jarInput.status.name}:" + jarInput.file.absolutePath)
                        onIncrementalJarTransform(jarInput.status, jarInput, dest)
                    }
                    Status.REMOVED -> {
                        //it seemed transform will full build when remove jar file, so ignore this status
                        Log.p("jar removed:" + jarInput.file.absolutePath)
                        if (dest.exists()) {
                            FileUtils.forceDelete(dest)
                        }
                    }
                    else -> {
                        if (!isIncremental) {
                            onIncrementalJarTransform(Status.ADDED, jarInput, dest)
                        }
                    }
                }
            }
            it.directoryInputs.forEach dirInput@{ dirInput ->
                val outputDir = getOutputDir(outputProvider, dirInput)
                if (!isIncremental) {
                    onDirTransform(dirInput.file, outputDir)
                    return@dirInput
                }
                onIncrementalDirInput(outputDir, dirInput)
            }
        }
    }

    private fun onIncrementalDirInput(outputDir: File, dirInput: DirectoryInput) {
        val srcPath = dirInput.file.absolutePath
        val destPath = outputDir.absolutePath
        val executorList = mutableListOf<() -> Unit>()
        dirInput.changedFiles.forEach { (file, status) ->
            if (file.isDirectory) {
                return@forEach
            }
            val destClassFilePath = file.absolutePath.replace(srcPath, destPath)
            val destFile = File(destClassFilePath)
            when (status) {
                Status.ADDED, Status.CHANGED -> {
//                    Log.test("dir ${status.name}:${file.absolutePath}")
                    executorList.add {
                        onSingleFileTransform(status, file, outputDir, destFile)
                    }
                }
                Status.REMOVED -> {
//                    Log.test("dir removed:${file.absolutePath}")
                    onRemovedFileTransform(outputDir, destFile)
                    if (destFile.exists()) {
                        FileUtils.forceDelete(destFile)
                    }
                }
                else -> {
                    Log.test("dir no changed:${file.absolutePath}")
                }
            }
        }
        executorList.forEach {
            it.invoke()
        }
    }

    open fun onIncrementalJarTransform(status: Status, jarInput: JarInput, destFile: File) {
        FileUtils.copyFile(jarInput.file, destFile)
    }

    open fun onDirTransform(inputDir: File, outputDir: File) {
        FileUtils.copyDirectory(inputDir, outputDir)
    }

    open fun onSingleFileTransform(status: Status, inputFile: File, outputDir: File, destFile: File) {
        FileUtils.copyFile(inputFile, destFile)
    }

    open fun onRemovedFileTransform(outputDir: File, destFile: File) {

    }
}