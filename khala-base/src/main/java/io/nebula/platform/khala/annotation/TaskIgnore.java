package io.nebula.platform.khala.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link io.nebula.platform.khala.combine.InitTask}的实现类默认都会被收集并作为初始化链的一部分，如果需要做一个
 * 公有父类，其中的逻辑不需要加入初始化流程，就可以通过该注解让插件编译时忽视被注解的类。
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-16 15:57
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface TaskIgnore {
}
