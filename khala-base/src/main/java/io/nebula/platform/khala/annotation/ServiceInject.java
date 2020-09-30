package io.nebula.platform.khala.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 目前项目中未使用，原本计划是用此注解更加简便注册componentService
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-16 15:58
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ServiceInject {
    String alias() default "";
}
