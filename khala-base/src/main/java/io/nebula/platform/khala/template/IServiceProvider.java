package io.nebula.platform.khala.template;

import java.util.HashMap;

import io.nebula.platform.khala.IComponentService;

/**
 * gather all service in component and provide them by implementation of this interface
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-29 22:33
 */
public interface IServiceProvider {
    HashMap<Class<? extends IComponentService>, ? extends IComponentService> getComponentServices();

    HashMap<String, ? extends IComponentService> getAliasComponentServices();
}
