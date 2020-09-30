package io.nebula.platform.khala.plugin.tasks

import io.nebula.platform.khala.plugin.resolve.PrefixHelper
import io.nebula.platform.khala.plugin.utils.AndroidXmlHelper
import io.nebula.platform.khala.plugin.utils.Log
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.dom4j.QName
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter

/**
 * refine manifest, remove all attributes which will define in application's manifest. This task only
 * run in component package.
 * @author panxinghai
 *
 * date : 2019-07-18 15:20
 */
open class RefineManifest : AndroidVariantTask() {
    var manifestFile: File? = null

    @TaskAction
    fun taskAction() {
        val reader = SAXReader()
        val document = reader.read(manifestFile)
        val root = document.rootElement

        //remove versionCode and versionName attribute in <manifest>
        root.remove(root.attribute(AndroidXmlHelper.getAndroidQName("versionCode")))
        root.remove(root.attribute(AndroidXmlHelper.getAndroidQName("versionName")))

        //remove uses-sdk tag
        root.remove(root.element("uses-sdk"))

        val applicationElement = root.element(AndroidXmlHelper.TAG_APPLICATION)
        //remove application all attributes
        val attributeNameList = mutableListOf<QName>()
        var applicationName = ""
        applicationElement.attributes().forEach {
            attributeNameList.add(it.qName)
            if (it.qName == AndroidXmlHelper.getAndroidQName("name")) {
                applicationName = it.value
            }
        }
        Log.d("applicationName = $applicationName")
        attributeNameList.forEach {
            applicationElement.remove(applicationElement.attribute(it))
        }

        //remove action and category that equals MAIN/LAUNCHER in <intent-filter>
        applicationElement.elementIterator(AndroidXmlHelper.TAG_ACTIVITY).forEach { activityElement ->
            activityElement.elementIterator("intent-filter").forEach { element ->
                element.elementIterator("action").forEach {
                    val attribute = it.attribute(AndroidXmlHelper.getAndroidQName("name"))
                    if (attribute.value == AndroidXmlHelper.ACTION_MAIN) {
                        element.remove(it)
                    }
                }
                element.elementIterator("category").forEach {
                    val attribute = it.attribute(AndroidXmlHelper.getAndroidQName("name"))
                    if (attribute.value == AndroidXmlHelper.CATEGORY_LAUNCHER) {
                        element.remove(it)
                    }
                }
            }
        }

        applicationElement.elementIterator(AndroidXmlHelper.TAG_ACTIVITY).forEach { activityElement ->
            val intentFilterElement = activityElement.element("intent-filter")
            if (intentFilterElement != null && intentFilterElement.attributeCount() == 0) {
                activityElement.remove(intentFilterElement)
            }
        }
        PrefixHelper.instance.prefixResourceFile(applicationElement)
        FileWriter(manifestFile!!).use {
            XMLWriter(it).apply {
                write(root)
            }
        }

    }

    class ConfigAction(private val scope: VariantScope,
                       private val manifestFile: File) :
            VariantTaskCreationAction<RefineManifest>(scope) {
        override val name: String
            get() = scope.getTaskName("Refine", "Manifest")


        override val type: Class<RefineManifest>
            get() = RefineManifest::class.java


        override fun configure(task: RefineManifest) {
            task.manifestFile = manifestFile
            task.variantName = scope.fullVariantName

//            scope.taskContainer.pluginRefineManifest = task
//            task.dependsOn(scope.taskContainer.pluginPrefixResources)
            task.dependsOn(scope.taskContainer.processManifestTask)
        }
    }
}