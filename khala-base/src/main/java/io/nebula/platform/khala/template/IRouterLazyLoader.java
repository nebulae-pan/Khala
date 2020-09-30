package io.nebula.platform.khala.template;

import android.util.SparseArray;

import java.util.List;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-04 15:56
 */
public interface IRouterLazyLoader {
    List<IRouterNodeProvider> lazyLoadFactoryByGroup(String group);

    SparseArray<String> loadServiceAliasMap();
}
