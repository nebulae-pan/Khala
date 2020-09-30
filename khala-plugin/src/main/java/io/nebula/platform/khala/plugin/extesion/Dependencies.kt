package io.nebula.platform.khala.plugin.extesion

import org.gradle.api.Project
import java.io.File


/**
 * Created by nebula on 2019-07-21
 */
open class Dependencies {
    private val dependenciesCollection = mutableListOf<File>()
    private val dependenciesPath = mutableListOf<String>()
    private val interfacesPath = mutableListOf<String>()

    fun implementation(path: String) {
        dependenciesPath.add(path)
    }

    fun interfaceApi(path: String) {
        interfacesPath.add(path)
    }

    internal fun appendDependencies(project: Project, addRuntimeDependencies: Boolean) {
        if (!addRuntimeDependencies) {
            return
        }
        dependenciesPath.forEach {
            project.dependencies.add("implementation", it)
        }
    }

    internal fun appendInterfaceApis(project: Project, addRuntimeDependencies: Boolean) {
        interfacesPath.forEach {
            project.dependencies.add("compileOnly", "$it@jar")
            if (addRuntimeDependencies) {
                project.dependencies.add("implementation", "$it@aar")
            }
        }
    }

//    private fun parse(parser: NotationConverter<String, Dependency>, value: String): Dependency {
//        return NotationParserBuilder.toType(Dependency::class.java).fromCharSequence(parser).toComposite().parseNotation(value)
//    }

    /**
     * resolve component dependencies, format like after:{name:version[:variant]}
     * @param extension extension created in plugin
     */
    internal fun resolveDependencies(extension: ComponentExtension) {
        dependenciesPath.forEach {
            val strings = it.split(':')
            val size = strings.size
            require(!(size < 2 || size > 3)) {
                "wrong format: $it. implementation format must be \$componentName:\$version[:\$variantName]"
            }
            val name = strings[0]
            val version = strings[1]
            val variant = if (size == 3) {
                strings[2]
            } else {
                "release"
            }
            val path = "${extension.repoPath}/$name/$version/$variant/component.aar"
            val file = File(path)
            require(file.exists()) { "can not resolve implementation:${file.absolutePath}" }
            dependenciesCollection.add(file)
        }
    }
}