package io.nebula.platform.khala.plugin.tasks.transform

import io.nebula.platform.khala.plugin.tasks.transform.*
import io.nebula.platform.khala.plugin.utils.InjectHelper
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.common.collect.ImmutableSet
import org.gradle.api.Project

/**
 * @author panxinghai
 *
 * date : 2019-10-14 20:14
 */
class KhalaLibTransform(private val project: Project) : BaseActuatorSetTransform() {
    override fun getTransformActuatorSet(): Set<TransformActuator> {
        return ImmutableSet.of(
                PrefixRActuator(project, true),
                RouterCompileActuator(project, true),
                InitTaskCompileActuator(project, true)
        )
    }

    override fun preTraversal(transformInvocation: TransformInvocation) {
        InjectHelper.instance.refresh()
        super.preTraversal(transformInvocation)
    }

    override fun getName(): String {
        return "khalaLib"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.PROJECT_ONLY
    }
}