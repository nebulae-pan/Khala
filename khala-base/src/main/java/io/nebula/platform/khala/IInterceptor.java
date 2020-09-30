package io.nebula.platform.khala;


import io.nebula.platform.khala.node.RouterNode;

/**
 * 普通的拦截器
 * <p>
 * Created by nebula on 2020-02-13
 */
public interface IInterceptor {
    void intercept(Chain chain);

    interface Chain {
        RouterNode node();

        void proceed(RouterNode node);
    }
}
