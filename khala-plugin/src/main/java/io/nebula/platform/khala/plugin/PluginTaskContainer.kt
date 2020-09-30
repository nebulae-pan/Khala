package io.nebula.platform.khala.plugin

import io.nebula.platform.khala.plugin.tasks.*
import org.gradle.api.Task


/**
 * task 实例holder，每个variant一个，方便在该variantScope下进行使用
 *
 * @author panxinghai
 *
 * date : 2019-07-11 15:26
 */
class PluginTaskContainer {
    var pluginRefineManifest: RefineManifest? = null
    var prefixResources: PrefixResources? = null
    var uploadTask: Task? = null
    var generateSymbol: GenerateSymbol? = null
    var genInterface: GenerateInterfaceArtifact? = null
}