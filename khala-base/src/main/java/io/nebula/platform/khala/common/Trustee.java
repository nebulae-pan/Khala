package io.nebula.platform.khala.common;

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.nebula.platform.khala.Constants;
import io.nebula.platform.khala.IComponentService;
import io.nebula.platform.khala.exception.ComponentServiceInstantException;
import io.nebula.platform.khala.exception.HashCollisionException;
import io.nebula.platform.khala.node.ComponentServiceNode;
import io.nebula.platform.khala.node.RouterNode;
import io.nebula.platform.khala.template.IRouterLazyLoader;
import io.nebula.platform.khala.template.IRouterNodeProvider;

/**
 * 数据集合，路由的信息都会通过该类加载，并且相关的组会在此缓存
 *
 * @author panxinghai
 * <p>
 * date : 2019-09-11 15:34
 */
public class Trustee {
    private static volatile Trustee sInstance;

    private SparseArray<RouterTable> mRouterMapByGroup;
    private IRouterLazyLoader mLazyLoader;
    private Map<String, IComponentService> mServiceInstanceContainer;

    private SparseArray<String> mComponentServiceAliasMap;

    private Trustee() {
        mRouterMapByGroup = new SparseArray<>();
    }

    public static Trustee instance() {
        if (sInstance == null) {
            synchronized (Trustee.class) {
                if (sInstance == null) {
                    sInstance = new Trustee();
                }
            }
        }
        return sInstance;
    }

    public synchronized void putRouterNode(RouterNode node) {
        String group = getGroupByNode(node);
        RouterTable table = getRouterTable(group);
        table.putNode(node);
    }

    public synchronized RouterNode getRouterNode(String path) {
        String group = getGroupByPath(path);
        RouterTable table = getRouterTable(group);
        return table.getNode(path);
    }

    public synchronized void release() {
        mServiceInstanceContainer = null;
        mRouterMapByGroup = null;
        mLazyLoader = null;
        sInstance = null;
    }

    public synchronized IComponentService instanceComponentService(Context context, Class<?> clazz)
            throws IllegalAccessException, InstantiationException, RuntimeException {
        if (mComponentServiceAliasMap == null) {
            IRouterLazyLoader lazyLoader = instanceLoader();
            if (lazyLoader == null) {
                throw new ComponentServiceInstantException("load node info by lazy load occur exception");
            }
            mComponentServiceAliasMap = lazyLoader.loadServiceAliasMap();
        }
        if (mComponentServiceAliasMap == null) {
            throw new ComponentServiceInstantException("load service alias occur exception.");
        }
        String path = mComponentServiceAliasMap.get(clazz.getName().hashCode());
        if (TextUtils.isEmpty(path)) {
            throw new ComponentServiceInstantException("cannot find router info with class:" + clazz.getName() + ", path:" + path);
        }
        RouterNode node = getRouterNode(path);
        if (node == null) {
            throw new ComponentServiceInstantException("cannot find router info with class:" + clazz.getName() + ", path:" + path);
        }
        return instanceComponentService(context, (ComponentServiceNode) node);
    }

    public synchronized IComponentService instanceComponentService(Context context, ComponentServiceNode node)
            throws InstantiationException, IllegalAccessException {
        if (mServiceInstanceContainer == null) {
            mServiceInstanceContainer = new HashMap<>();
        }
        IComponentService instance = mServiceInstanceContainer.get(node.getPath());
        if (instance == null) {
            instance = (IComponentService) node.getTarget().newInstance();
            instance.init(context);
            mServiceInstanceContainer.put(node.getPath(), instance);
        }
        return instance;
    }

    private RouterTable getRouterTable(String group) {
        //when first access, load RouterNodeFactory to get RouterNode
        RouterTable table = mRouterMapByGroup.get(group.hashCode());
        if (table == null) {
            table = new RouterTable();
            mRouterMapByGroup.put(group.hashCode(), table);
            loadNode(group, table);
        }
        return table;
    }

    private String getGroupByNode(RouterNode node) {
        String[] strings = node.getPath().split("/");
        if (strings.length < 3) {
            throw new IllegalArgumentException("invalid router path: \"" + node.getPath() + "\" in " + node.getTarget() + ". Router Path must starts with '/' and has a group segment");
        }
        return strings[1];
    }

    private String getGroupByPath(String path) {
        String[] strings = path.split("/");
        if (strings.length < 3) {
            throw new IllegalArgumentException("invalid router path: \"" + path + "\".");
        }
        return strings[1];
    }

    //lazy load node
    private synchronized void loadNode(String group, RouterTable table) {
        IRouterLazyLoader lazyLoader = instanceLoader();
        if (lazyLoader == null) {
            return;
        }
        List<IRouterNodeProvider> list = lazyLoader.lazyLoadFactoryByGroup(group);
        for (IRouterNodeProvider factory : list) {
            List<RouterNode> nodes = factory.getRouterNodes();
            for (RouterNode node : nodes) {
                table.putNode(node);
            }
        }
    }

    private IRouterLazyLoader instanceLoader() {
        if (mLazyLoader == null) {
            try {
                mLazyLoader = (IRouterLazyLoader) Class.forName(Constants.ROUTER_GEN_FILE_PACKAGE + Constants.LAZY_LOADER_IMPL_NAME)
                        .newInstance();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return mLazyLoader;
    }

    private static class RouterTable {
        SparseArray<RouterNode> nodeMap;

        void putNode(RouterNode node) {
            if (nodeMap == null) {
                nodeMap = new SparseArray<>();
            }
            nodeMap.put(node.getPath().hashCode(), node);
        }

        RouterNode getNode(String path) {
            if (nodeMap == null) {
                return null;
            }
            RouterNode node = nodeMap.get(path.hashCode());
            if (node != null && !node.getPath().equals(path)) {
                throw new HashCollisionException(path, node.getPath());
            }
            return node;
        }
    }
}
