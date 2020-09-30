package io.nebula.platform.khala;

import io.nebula.platform.khala.node.ActivityNode;
import io.nebula.platform.khala.node.FragmentNode;
import io.nebula.platform.khala.node.NodeType;
import io.nebula.platform.khala.node.RouterNodeFactory;
import io.nebula.platform.khala.template.IInjectable;
import io.nebula.platform.khala.template.IRouterLazyLoader;
import io.nebula.platform.khala.template.IRouterNodeProvider;
import io.nebula.platform.khala.template.IServiceAliasProvider;

/**
 * Created by nebula on 2019-07-21
 */
public class Constants {
    public final static String FRAME_NAME = "khala";
    public final static String GEN_PACKAGE_NAME = "io.nebula.platform." + FRAME_NAME + ".gen.";
    public final static String GEN_FOLDER_NAME = "io/nebula/platform/" + FRAME_NAME + "/gen/";

    public final static String ROUTER_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "router.";
    public final static String ROUTER_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "router/";
    public final static String INIT_TASK_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "task.";
    public final static String INIT_TASK_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "task/";
    //consider remove
    public final static String SERVICE_GEN_FILE_PACKAGE = GEN_PACKAGE_NAME + "service.";
    public final static String SERVICE_GEN_FILE_FOLDER = GEN_FOLDER_NAME + "service/";

    public final static String SERVICE_ALIAS_PROVIDER_FILE_PACKAGE = GEN_PACKAGE_NAME + "servicealias.";
    public final static String SERVICE_ALIAS_PROVIDER_FILE_FOLDER = GEN_FOLDER_NAME + "servicealias/";


    public final static String LAZY_LOADER_IMPL_NAME = "KhalaRouterLazyLoaderImpl";
    public final static String INIT_TASK_COLLECTOR_IMPL_NAME = "InitTaskCollectorImpl";
    public final static String SERVICE_COLLECTOR_IMPL_NAME = "ServiceCollectorImpl";

    public final static String KHALA_ROUTER_CLASSNAME = KhalaRouter.class.getName();
    public final static String ACTIVITY_NODE_CLASSNAME = ActivityNode.class.getName();
    public final static String FRAGMENT_NODE_CLASSNAME = FragmentNode.class.getName();
    public final static String NODE_TYPE_CLASSNAME = NodeType.class.getName();
    public final static String NODE_FACTORY_CLASSNAME = RouterNodeFactory.class.getName();
    public final static String ROUTER_LAZY_LOADER_CLASSNAME = IRouterLazyLoader.class.getName();
    public final static String ROUTER_PROVIDER_CLASSNAME = IRouterNodeProvider.class.getName();
    public final static String SERVICE_ALIAS_PROVIDER_CLASSNAME = IServiceAliasProvider.class.getName();
    public final static String COMPONENT_SERVICE_CLASSNAME = IComponentService.class.getName();
    public final static String INJECTABLE_CLASSNAME = IInjectable.class.getName();
    public final static String REPLACE_META_NAME = "khala_replace_real_app";
    public final static String REPLACE_APPLICATION_NAME = "io.nebula.platform.khala.combine.KhalaApplication";

    public final static String COMPONENT_APPLICATION_NAME = "io.nebula.platform.khala.combine.ComponentApplication";
}
