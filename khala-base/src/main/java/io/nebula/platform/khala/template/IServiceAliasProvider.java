package io.nebula.platform.khala.template;

import android.util.SparseArray;

/**
 * Service class 和Router path的关联集合
 * <p>
 * Created by nebula on 2019-12-07
 */
public interface IServiceAliasProvider {
    SparseArray<String> getServiceAlias();
}
