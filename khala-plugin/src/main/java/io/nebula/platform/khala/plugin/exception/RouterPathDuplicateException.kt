package io.nebula.platform.khala.plugin.exception

/**
 * Created by nebula on 2019-12-11
 */
class RouterPathDuplicateException(path1: String, class1: String,
                                   path2: String, class2: String)
    : RuntimeException("Router path duplicate, please check: [$path1]:$class1 - [$path2]:$class2")