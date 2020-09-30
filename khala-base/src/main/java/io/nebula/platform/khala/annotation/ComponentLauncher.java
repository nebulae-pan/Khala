package io.nebula.platform.khala.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 被注解的Activity会被认为是模块单独运行时的入口，在打包为aar时会被忽略。目前项目中未使用
 * Created by nebula on 2019-09-28
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ComponentLauncher {
}
