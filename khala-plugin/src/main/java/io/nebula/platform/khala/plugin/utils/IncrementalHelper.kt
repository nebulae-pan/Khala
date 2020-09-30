package io.nebula.platform.khala.plugin.utils

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import javassist.CtClass
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.lang.reflect.Type

/**
 * 该工具类提供功能：用于储存transform中增量编译的中间信息，配合[Persistent]注解使用
 * @author panxinghai
 *
 * date : 2020-01-14 17:02
 */
object IncrementalHelper {
    private lateinit var gson: Gson

    init {
        registerGsonAdapter()
    }

    private fun saveInfo(info: Any?, type: Type, fileName: String, outputDir: File) {
        if (info == null) {
            return
        }
        val string = gson.toJson(info, type)
        val file = File(outputDir, "persistence-${DigestUtils.md5Hex(fileName)}.json")
        file.writeText(string)
    }

    private fun <T> loadInfo(type: Type, fileName: String, outputDir: File): T? {
        val file = File(outputDir, "persistence-${DigestUtils.md5Hex(fileName)}.json")
        if (!file.exists()) {
            return null
        }
        val string = file.readText()
        return gson.fromJson<T>(string, type)
    }

    /**
     * 储存所有被[Persistent]注解的field，将其内容转换为json保存
     * @param target 目标类
     * @param outputDir 储存目标文件夹
     */
    fun savePersistentField(target: Any, outputDir: File) {
        target.javaClass.declaredFields.forEach {
            val persistent = it.getAnnotation(Persistent::class.java) ?: return@forEach
            val fileName = if (persistent.fileName == "") it.name else persistent.fileName
            it.isAccessible = true
            saveInfo(it.get(target), it.genericType, fileName, outputDir)
        }
    }

    /**
     * 将先前储存的数据根据md5后的文件名(field名）还原至被[Persistent]注解的field
     * @param target 目标类
     * @param outputDir 储存目标文件夹
     */
    fun loadPersistentField(target: Any, outputDir: File) {
        target.javaClass.declaredFields.forEach {
            val persistent = it.getAnnotation(Persistent::class.java) ?: return@forEach
            val fileName = if (persistent.fileName == "") it.name else persistent.fileName
            val info = loadInfo<Any>(it.genericType, fileName, outputDir)
            if (info != null) {
                it.isAccessible = true
                it.set(target, info)
            }
        }
    }

    private fun registerGsonAdapter() {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(CtClass::class.java, CtClassTypeAdapter())
                .registerTypeAdapter(Collection::class.java, CollectionAdapter())
        gson = gsonBuilder.create()
    }

    /**
     * Json解析过程中一些特殊类的处理，比如CtClass。某些情况下，比如删除了一个文件，可能对应的CtClass会为空，为了
     * 处理这样的情况，如果某些数据关于CtClass的项被删除，就将其设置为原始类型void（这个类型正常情况下不会出现），由此
     * 来判断数据的删除情况。
     */
    private class CtClassTypeAdapter : TypeAdapter<CtClass>() {
        override fun write(out: JsonWriter?, value: CtClass?) {
            if (out == null) {
                return
            }
            out.beginObject()
            out.name("javassist-ctClass").value(value?.name)
            out.endObject()
        }

        override fun read(`in`: JsonReader?): CtClass {
            var ctClass: CtClass = CtClass.voidType
            if (`in` == null) {
                return ctClass
            }
            `in`.beginObject()
            while (`in`.hasNext()) {
                if (`in`.nextName() == "javassist-ctClass") {
                    try {
                        ctClass = InjectHelper.instance.getClassPool()[`in`.nextString()]
                    } catch (e: Exception) {
                        //cannot got ctClass, its most likely the class has been deleted
                        Log.w(e.toString())
                    }
                }
                break
            }
            `in`.endObject()
            return ctClass
        }
    }

    class CollectionAdapter : JsonDeserializer<Collection<*>> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Collection<*>? {
            val collection = context?.deserialize<Collection<*>>(json, typeOfT)
            if (collection != null) {
                val iterator = collection.iterator()
                if (iterator is MutableIterator) {
                    iterator.forEach {
                        if (it is CtClass && it == CtClass.voidType) {
                            Log.e("remove ct class. type:$typeOfT.")
                            iterator.remove()
                            return@forEach
                        }
                        if (it == null) {
                            iterator.remove()
                            return@forEach
                        }
                    }
                }
            }
            return collection
        }
    }

//    class MapAdapter : JsonDeserializer<Map<*, CtClass>> {
//        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Collection<CtClass>? {
//            val collection = context?.deserialize<Collection<CtClass>>(json, typeOfT)
//            if (collection != null) {
//                val iterator = collection.iterator()
//                if (iterator is MutableIterator) {
//                    iterator.forEach {
//                        if (it == CtClass.voidType) {
//                            Log.e("remove ct class. type:$typeOfT.")
//                            iterator.remove()
//                        }
//                    }
//                }
//            }
//            return collection
//        }
//    }
}