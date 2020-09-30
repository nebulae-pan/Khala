package io.nebula.platform.khala.plugin.tasks.transform

import io.nebula.platform.khala.plugin.resolve.ZipHelper
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.Status
import java.io.File
import java.util.zip.ZipEntry

/**
 * @author panxinghai
 *
 * date : 2019-11-18 19:56
 */
abstract class TypeActuator(protected val isComponent: Boolean) : TransformActuator {

    override fun onJarVisited(status: Status, jarInput: JarInput) {
        ZipHelper.traversalZip(jarInput.file) { entry ->
            onJarEntryVisited(entry, jarInput.file)
        }
    }

    abstract fun onJarEntryVisited(zipEntry: ZipEntry,
                                   jarFile: File)
}