package io.nebula.platform.khala;

import android.annotation.SuppressLint;

import java.util.HashMap;

import io.nebula.platform.khala.combine.InitTaskManager;
import io.nebula.platform.khala.template.IServiceCollector;


/**
 * @author panxinghai
 * <p>
 * date : 2019-10-14 18:07
 */
@SuppressWarnings("unused")
public class Khala {
    @SuppressLint("StaticFieldLeak")
    private volatile static Khala sInstance;
    private HashMap<String, IComponentService> mServiceAliasMap;
    private HashMap<Class<? extends IComponentService>, IComponentService> mServiceClassMap;
    private InitTaskManager mTaskManager = InitTaskManager.instance();

    public static Khala instance() {
        if (sInstance == null) {
            synchronized (Khala.class) {
                if (sInstance == null) {
                    sInstance = new Khala();
                }
            }
        }
        return sInstance;
    }

    public void registerService(String alias, IComponentService service) {
        loadAllComponentService();
        mServiceAliasMap.put(alias, service);
    }

    public void registerService(IComponentService service) {
        loadAllComponentService();
        mServiceClassMap.put(service.getClass(), service);
    }

    @SuppressWarnings("unchecked")
    public <T extends IComponentService> T service(Class<T> clazz) {
        loadAllComponentService();
        return (T) mServiceClassMap.get(clazz);
    }

    public IComponentService service(String alias) {
        loadAllComponentService();
        return mServiceAliasMap.get(alias);
    }

    public InitTaskManager getTaskManager() {
        return mTaskManager;
    }

    private void loadAllComponentService() {
        if (mServiceClassMap != null) {
            return;
        }
        IServiceCollector serviceCollector = null;
        try {
            serviceCollector = (IServiceCollector) Class
                    .forName(Constants.SERVICE_GEN_FILE_PACKAGE + Constants.SERVICE_COLLECTOR_IMPL_NAME)
                    .newInstance();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        if (serviceCollector == null) {
            return;
        }
        if (mServiceAliasMap == null) {
            mServiceClassMap = new HashMap<>();
        }
        mServiceClassMap.putAll(serviceCollector.gatherServices());
        if (mServiceAliasMap == null) {
            mServiceAliasMap = new HashMap<>();
        }
        mServiceAliasMap.putAll(serviceCollector.gatherAliasServices());
    }
}
