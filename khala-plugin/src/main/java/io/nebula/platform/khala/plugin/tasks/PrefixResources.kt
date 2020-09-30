package io.nebula.platform.khala.plugin.tasks

import io.nebula.platform.khala.plugin.resolve.PrefixHelper
import io.nebula.platform.khala.plugin.utils.Log
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Created by nebula on 2019-08-15
 */
open class PrefixResources : AndroidVariantTask() {
    var packagedResFolder: File? = null
    var prefix: String = ""

    @TaskAction
    fun taskAction() {
        val folder = packagedResFolder ?: return
        val startTime = System.currentTimeMillis()
        PrefixHelper.instance.initWithPackagedRes(prefix, folder)
        folder.walk().filter { it.isFile && it.name != "values.xml" }
                .forEach {
                    PrefixHelper.instance.prefixResourceFile(it)
                }
        PrefixHelper.instance.prefixValues(File(folder, "values/values.xml"))
        Log.i("prefix resources cost: ${System.currentTimeMillis() - startTime}ms")
    }

    class ConfigAction(private val scope: VariantScope,
                       private val packagedResFolder: File,
                       private val prefix: String) :
            VariantTaskCreationAction<PrefixResources>(scope) {
        override val type: Class<PrefixResources>
            get() = PrefixResources::class.java


        override val name: String
            get() = scope.getTaskName("prefix", "Resources")

        override fun configure(task: PrefixResources) {
            super.configure(task)
            task.variantName = scope.fullVariantName
            task.packagedResFolder = packagedResFolder
            task.prefix = prefix
        }
    }
}