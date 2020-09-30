package io.nebula.platform.khala.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解判断是否需要暴露给外部，被ClassExposed注解的类和其引用到的类，都会加入到interface.jar中
 * <p>
 * Created by nebula on 2020-03-24
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ClassExposed {

}
