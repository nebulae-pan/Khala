package io.nebula.platform.khala.plugin.tasks

import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.TaskInputHelper
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.symbols.processLibraryMainSymbolTable
import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.parseResourceSourceSetDirectory
import com.google.common.base.Strings
import com.google.common.collect.Iterables
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import java.io.File
import java.io.IOException
import java.util.function.Supplier

/**
 * 在修改完前缀后重新生成R.txt, 该类修改自[GenerateLibraryRFileTask]
 *
 * Created by nebula on 2019-08-26
 */
open class GenerateSymbol : ProcessAndroidResources() {
    @get:OutputDirectory
    @get:Optional
    var sourceOutputDirectory: File? = null
        private set

    @Input
    fun outputSources() = sourceOutputDirectory != null

    @get:OutputFile
    @get:Optional
    var rClassOutputJar: File? = null
        private set

    @Input
    fun outputRClassJar() = rClassOutputJar != null

    override fun getSourceOutputDir() = sourceOutputDirectory ?: rClassOutputJar

    @get:OutputFile
    lateinit var textSymbolOutputFile: File
        private set

    @get:OutputFile
    lateinit var symbolsWithPackageNameOutputFile: File
        private set

    @get:OutputFile
    @get:Optional
    var proguardOutputFile: File? = null
        private set

    @Suppress("unused")
    // Needed to trigger rebuild if proguard file is requested (https://issuetracker.google.com/67418335)
    @Input
    fun hasProguardOutputFile() = proguardOutputFile != null

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var dependencies: FileCollection
        private set

    @get:Internal
    lateinit var packageForRSupplier: Supplier<String>
        private set
    @Suppress("MemberVisibilityCanBePrivate")
    @get:Input
    val packageForR
        get() = packageForRSupplier.get()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    lateinit var platformAttrRTxt: FileCollection
        private set

    @get:Internal
    lateinit var applicationIdSupplier: Supplier<String>
        private set
    @get:Input
    val applicationId
        get() = applicationIdSupplier.get()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var inputResourcesDir: BuildableArtifact
        private set

    @get:Input
    var namespacedRClass: Boolean = false
        private set

    @Throws(IOException::class)
    override fun doFullTaskAction() {
        val manifest = Iterables.getOnlyElement(
                ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifestFiles))
                .outputFile

        val androidAttrSymbol = getAndroidAttrSymbols(platformAttrRTxt.singleFile)

        val symbolTable = parseResourceSourceSetDirectory(
                inputResourcesDir.single(),
                IdProvider.sequential(),
                androidAttrSymbol)

        processLibraryMainSymbolTable(
                librarySymbols = symbolTable,
                libraries = this.dependencies.files,
                mainPackageName = packageForR,
                manifestFile = manifest,
                sourceOut = sourceOutputDirectory,
                rClassOutputJar = rClassOutputJar,
                symbolFileOut = textSymbolOutputFile,
                platformSymbols = androidAttrSymbol,
                namespacedRClass = namespacedRClass)

        SymbolIo.writeSymbolListWithPackageName(
                textSymbolOutputFile.toPath(),
                manifest.toPath(),
                symbolsWithPackageNameOutputFile.toPath())
    }

    private fun getAndroidAttrSymbols(androidJar: File) =
            if (androidJar.exists())
                SymbolIo.readFromAapt(androidJar, "android")
            else
                SymbolTable.builder().tablePackage("android").build()

    class CreationAction(
            variantScope: VariantScope,
            private val symbolFile: File,
            private val symbolsWithPackageNameOutputFile: File,
            private val externalDir: File
    ) : VariantTaskCreationAction<GenerateSymbol>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("generate", "PrefixRFile")
        override val type: Class<GenerateSymbol>
            get() = GenerateSymbol::class.java

        private lateinit var rClassOutputJar: File
        private lateinit var sourceOutputDirectory: File

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            if (variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
                rClassOutputJar = File(externalDir, "R.jar")
            } else {
                sourceOutputDirectory = externalDir
            }

//            if (generatesProguardOutputFile(variantScope)) {
//                variantScope
//                        .artifacts
//                        .appendArtifact(
//                                InternalArtifactType.AAPT_PROGUARD_FILE,
//                                listOf(variantScope.processAndroidResourcesProguardOutputFile),
//                                taskName)
//            }
        }

        override fun handleProvider(taskProvider: TaskProvider<out GenerateSymbol>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.processAndroidResTask = taskProvider
        }

        override fun configure(task: GenerateSymbol) {
            super.configure(task)

            task.platformAttrRTxt = variantScope.globalScope.platformAttrs

            task.applicationIdSupplier = TaskInputHelper.memoize {
                variantScope.variantData.variantConfiguration.applicationId
            }

            task.dependencies = variantScope.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.SYMBOL_LIST_WITH_PACKAGE_NAME)
            if (variantScope.globalScope.projectOptions.get(BooleanOption.ENABLE_SEPARATE_R_CLASS_COMPILATION)) {
                task.rClassOutputJar = rClassOutputJar
            } else {
                task.sourceOutputDirectory = sourceOutputDirectory
            }
            task.textSymbolOutputFile = symbolFile
            task.symbolsWithPackageNameOutputFile = symbolsWithPackageNameOutputFile

            if (generatesProguardOutputFile(variantScope)) {
                task.proguardOutputFile = variantScope.processAndroidResourcesProguardOutputFile
            }

            task.packageForRSupplier = TaskInputHelper.memoize {
                Strings.nullToEmpty(variantScope.variantConfiguration.originalApplicationId)
            }

            task.manifestFiles = variantScope.artifacts.getFinalProduct(InternalArtifactType.MERGED_MANIFESTS)

            task.inputResourcesDir = variantScope.artifacts.getFinalArtifactFiles(
                    InternalArtifactType.PACKAGED_RES)

            task.namespacedRClass = variantScope.globalScope.projectOptions[BooleanOption.NAMESPACED_R_CLASS]

            task.outputScope = variantScope.outputScope
        }
    }
}