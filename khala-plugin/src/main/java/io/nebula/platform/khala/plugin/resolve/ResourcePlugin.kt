package io.nebula.platform.khala.plugin.resolve

import io.nebula.platform.khala.plugin.resolve.arsc.ArscFile
import io.nebula.platform.khala.plugin.resolve.arsc.StringPoolChunk
import io.nebula.platform.khala.plugin.resolve.arsc.TableChunk
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import net.sf.json.JSONArray
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.*

/**
 * @author : panxinghai
 * date : 2019-07-02 17:22
 */
class ResourcePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("resource check plugin applied!")
        val appExtension = project.extensions.getByName("android") as AppExtension
        appExtension.applicationVariants.all {
            val variantData = (it as ApplicationVariantImpl).variantData
            val task = variantData.taskContainer.packageAndroidTask
            task?.get()?.doFirst {
                val resourceArtifact = task.get().resourceFiles
                resourceArtifact.files.forEach { file ->
                    replaceDuplicateResource(file)
                }
            }
        }
    }


//    //gradle 3.2.1
//    project.android.applicationVariants.all
//    {
//        variant ->
//        def variantData = variant . variantData
//                variantData.taskContainer.each {
//                    def task = it . packageAndroidTask
//                            task.doFirst {
//                                def resourceArtifact = task . getResourceFiles ()
//                                resourceArtifact.files.each {
//                                    replaceDuplicateResource(it)
//                                }
//                            }
//                }
//    }


    private fun replaceDuplicateResource(file: File) {
        val apFileName = readJsonFileAndGetPathValue(File(file, "output.json"))
        val zipFile = File(file, apFileName)
        var arscFile: File? = null
        val resourceFile: ArscFile?
        try {
            if (!zipFile.exists()) {
                println("file$zipFile not exist")
                return
            }
            arscFile = ZipHelper.unzipSpecialFile(zipFile, "resources.arsc", zipFile.parent)
            val duplicateCollection = DuplicateHelper.checkDuplicate(zipFile)
            val replaceTargetMap = mutableMapOf<String, String>()
            duplicateCollection.forEach {
                val target = it.value[0]
                for (i in 1 until it.value.size) {
                    replaceTargetMap[it.value[i]] = target
                    ZipHelper.removeZipEntry(zipFile, it.value[i])
                }
            }
            resourceFile = ArscFile.fromFile(arscFile)
            val chunks = resourceFile.chunks
            chunks.forEach {
                if (it is TableChunk) {
                    val stringPool = it.chunks[0] as StringPoolChunk
                    for (i in 0 until stringPool.stringCount) {
                        val key = stringPool.getString(i)
                        if (replaceTargetMap.containsKey(key)) {
                            replaceTargetMap[key]?.let { it1 -> stringPool.setString(i, it1) }
//                            println("replace $key to ${replaceTargetMap[key]}")
                        }
                    }
                }
            }

            arscFile.delete()
            arscFile = File(file, "resources.arsc")
            arscFile.createNewFile()

            val fis = FileOutputStream(arscFile)
            fis.use {
                fis.write(resourceFile.toByteArray())
            }

            ZipHelper.addZipEntry(zipFile, arscFile, "resources.arsc")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            arscFile?.delete()
        }

    }

    private fun readPlaintTextFile(file: File): String {
        val bufferedReader: BufferedReader
        val sb = StringBuilder()
        var tempString: String?
        val fileInputStream = FileInputStream(file)
        val inputStreamReader = InputStreamReader(fileInputStream, "UTF-8")
        bufferedReader = BufferedReader(inputStreamReader)
        bufferedReader.use { reader ->
            while (reader.readLine().also { tempString = it } != null) {
                sb.append(tempString)
            }
        }
        return sb.toString()
    }

    /**
     * 读output.json，拿到path字段，为对应的ap_文件名称
     * @param file
     */
    @Throws(IOException::class)
    fun readJsonFileAndGetPathValue(file: File): String {
        val jsonString = readPlaintTextFile(file)
        val jsonArray = JSONArray.fromObject(jsonString)
        val jsonObject = jsonArray.getJSONObject(0)
        return jsonObject.getString("path")
    }


//gradle plugin 3.4.1
//        project.android.applicationVariants.all { variant ->
//            def variantData = variant.variantData
//            variantData.taskContainer.each {
//                def task = it.packageAndroidTask.get()
//                task.doFirst {
//                    def resourceArtifact = task.getResourceFiles()
//                    resourceArtifact.files.each {
//                        def resourceDir = it
//                        def zipFile = new File(resourceDir, "resources-${variantData.name}.ap_")
//                        if (!zipFile.exists()) {
//                            println("file${zipFile.toString()} not exist")
//                            return
//                        }
//                        unzipSpecialFile(zipFile, "resources.arsc", zipFile.parent)
//                        def duplicateCollection = DuplicateHelper.checkDuplicate(zipFile)
//
//                    }
//                }
//            }
//        }
}