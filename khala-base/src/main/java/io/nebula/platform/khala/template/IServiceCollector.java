package io.nebula.platform.khala.template;

import java.util.HashMap;

import io.nebula.platform.khala.IComponentService;


/**
 * collect all service, not only application but component gather by {@link IServiceProvider#getComponentServices()}
 *
 * @author panxinghai
 * <p>
 * date : 2019-10-29 22:34
 */
public interface IServiceCollector {
    HashMap<Class<? extends IComponentService>, ? extends IComponentService> gatherServices();

    HashMap<String, ? extends IComponentService> gatherAliasServices();
}
