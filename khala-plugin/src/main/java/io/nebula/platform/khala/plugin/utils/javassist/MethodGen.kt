package io.nebula.platform.khala.plugin.utils.javassist

import io.nebula.platform.khala.plugin.exception.ClassGenerateException
import io.nebula.platform.khala.plugin.utils.InjectHelper
import io.nebula.platform.khala.plugin.utils.Log
import javassist.CtClass
import javassist.CtMethod

/**
 *
 * Javassist生成字节码的工具类
 * @author panxinghai
 *
 * date : 2019-10-29 23:00
 */
class MethodGen(private val className: String) {
    private var returnStatement = ""
    private var name = ""
    private var paramsStatement = ""
    private var methodBodyProvider: () -> String = { "{}" }
    private var interfaces = mutableListOf<CtClass>()
    private val classPool = InjectHelper.instance.getClassPool()

    fun signature(returnStatement: String = "void", name: String, paramsStatement: String = "()"): MethodGen {
        this.returnStatement = returnStatement
        this.name = name
        this.paramsStatement = paramsStatement
        return this
    }

    fun body(provider: () -> String): MethodGen {
        this.methodBodyProvider = provider
        return this
    }

    fun superClass(superClass: CtClass): MethodGen {
        return this
    }

    fun interfaces(vararg interfaces: Class<out Any>): MethodGen {
        interfaces.forEach {
            val ctClass = classPool.getOrNull(it.name)
                    ?: throw ClassGenerateException("cannot get ctClass of ${it.name}.")
            if (!ctClass.isInterface) {
                throw ClassGenerateException("class:${it.name} is not interface.")
            }
            this.interfaces.add(ctClass)
        }
        return this
    }

    fun gen(): CtClass? {
        try {
            var genClass: CtClass? = classPool.getOrNull(className)
            if (genClass == null) {
                genClass = classPool.makeClass(className)
                genClass.classFile.majorVersion = 52
                interfaces.forEach {
                    genClass.addInterface(it)
                }
            }
            if (genClass == null) {
                throw ClassGenerateException("cannot make class: $className.")
            }
            var method = getDeclaredMethod(genClass, name)
            if (method == null) {
                method = genCtMethod(genClass)
                genClass.addMethod(method)
            }
            if (genClass.isFrozen) {
                genClass.defrost()
            }
            method.setBody(methodBodyProvider.invoke())
            return genClass
        } catch (e: Exception) {
            e.printStackTrace()
            if (e.message != null) {
                Log.e(e.message!!)
            }
        }
        return null
    }

    private fun genCtMethod(genClass: CtClass): CtMethod {
        return CtMethod.make(tasksSrc(), genClass)
    }

    private fun tasksSrc(): String {
        return "$returnStatement $name$paramsStatement${methodBodyProvider.invoke()}"
    }

    private fun getDeclaredMethod(ctClass: CtClass, name: String): CtMethod? {
        ctClass.declaredMethods.forEach {
            if (it.name == name) {
                return it
            }
        }
        return null
    }
}
