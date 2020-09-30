package io.nebula.platform.khala.plugin.utils

/**
 * @author panxinghai
 *
 * date : 2019-09-27 23:13
 */
class Descriptor {
    companion object {
        fun getTaskNameWithoutModule(name: String): String {
            return name.substring(name.lastIndexOf(':') + 1)
        }

        fun getTaskModuleName(name: String): String {
            val index = name.lastIndexOf(':')
            if (index == -1) {
                return ""
            }
            val str = name.substring(0, index)
            if (str[0] == ':') {
                return str.substring(1, str.length)
            }
            return str
        }

        fun getClassNameByFileName(fileName: String): String {
            return fileName.substring(0, fileName.indexOf('.')).replace('/', '.')
        }
    }
}