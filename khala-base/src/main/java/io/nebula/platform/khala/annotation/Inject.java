package io.nebula.platform.khala.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject注解的field会通过插件自动生成解析逻辑。
 *
 * @author panxinghai
 * <p>
 * date : 2019-09-27 11:51
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface Inject {
    String name() default "";

    boolean require() default false;
}
