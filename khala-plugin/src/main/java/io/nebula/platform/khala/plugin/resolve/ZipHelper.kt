package io.nebula.platform.khala.plugin.resolve

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * @author : panxinghai
 * date : 2019-07-02 17:47
 */
object ZipHelper {
    fun unzipSpecialFile(zipFile: File, zipEntryName: String, path: String): File {
        val zip = ZipFile(zipFile, Charset.forName("GBK"))
        val file = File(path, zipEntryName)
        zip.entries().toList().forEach { zipEntry ->
            if (zipEntry.name == zipEntryName) {
                val fis = zip.getInputStream(zipEntry)
                val fos = FileOutputStream(file)
                val buf1 = ByteArray(1024)

                var len: Int
                fis.use { input ->
                    while (input.read(buf1).also { len = it } > 0) {
                        fos.write(buf1, 0, len)
                    }
                }
                return file
            }
        }
        return file
    }

    fun removeZipEntry(zipFile: File, entryName: String) {
        val env = mutableMapOf<String, String>()
        env["create"] = "false"

        val uri = URI.create("jar:file://" + zipFile.path)
        val zipFs = FileSystems.newFileSystem(uri, env)
        zipFs.use {
            Files.delete(zipFs.getPath(entryName))
        }
    }

    fun addZipEntry(zipFile: File, file: File, entryName: String) {
        val env = mutableMapOf<String, String>()
        env["create"] = "false"
        val uri = URI.create("jar:" + zipFile.toURI())
        FileSystems.newFileSystem(uri, env).use {
            val pathToAddFile = file.toPath()
            val pathInZipFile = it.getPath("/" + file.name)
            Files.copy(pathToAddFile, pathInZipFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun traversalZip(file: File, action: (ZipEntry) -> Unit) {
        val zip = ZipFile(file)
        zip.use {
            it.entries().iterator().forEach { entry ->
                action.invoke(entry)
            }
        }
    }

    fun entryNameToClassName(entryName: String): String {
        return entryName.trim().replace('/', '.').substring(0, entryName.length - 6)
    }
}