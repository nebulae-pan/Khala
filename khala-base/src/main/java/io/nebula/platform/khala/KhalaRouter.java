package io.nebula.platform.khala;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

import io.nebula.platform.khala.common.Trustee;
import io.nebula.platform.khala.exception.ComponentServiceInstantException;
import io.nebula.platform.khala.node.ComponentServiceNode;
import io.nebula.platform.khala.node.FragmentNode;
import io.nebula.platform.khala.node.RouterNode;
import io.nebula.platform.khala.template.IInjectable;


/**
 * Created by nebula on 2019-07-20
 */
@SuppressLint({"StaticFieldLeak"})
@SuppressWarnings({"unused", "WeakerAccess"})
public class KhalaRouter {
    private volatile static KhalaRouter sInstance;
    private volatile static boolean isInit = false;
    private static Context sContext;

    private SparseArray<IInterceptor> mInterceptors;
    private List<IInterceptor> mInterceptorList;
    private NavigateCallback mNavigateCallback;

    private KhalaRouter() {
    }

    public interface NavigateCallback {
        void onFound(RouterNode node);

        void onLost(String path);

        void onError(RouterNode node, Exception e);
    }

    public interface ServiceInstantCallback {
        void onError(Exception e);
    }

    public static void init(KhalaRouterConfig config) {
        if (isInit) {
            return;
        }
        isInit = true;
        if (config == null) {
            throw new IllegalArgumentException("KhalaRouterConfig cannot be null");
        }
        sContext = config.context;
        instance().mNavigateCallback = config.navigateCallback;
    }

    public static KhalaRouter instance() {
        if (!isInit) {
            throw new RuntimeException("KhalaRouter did't init");
        }
        if (sInstance == null) {
            synchronized (KhalaRouter.class) {
                if (sInstance == null) {
                    sInstance = new KhalaRouter();
                }
            }
        }
        return sInstance;
    }

    public static void inject(Object target) {
        if (target instanceof IInjectable) {
            ((IInjectable) target).autoSynthetic$FieldInjectKhala();
        }
    }

    public Navigator route(String path) {
        return new Navigator(path);
    }

    public void addRouterNode(RouterNode node) {
        Trustee.instance().putRouterNode(node);
    }

    @Nullable
    public <S> S service(Class<? extends S> clazz) {
        return service(null, clazz, null);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <S> S service(Context context, Class<? extends S> clazz, ServiceInstantCallback callback) {
        if (context == null) {
            context = sContext;
        }
        return (S) navigateService(context, clazz, callback);
    }

    public synchronized void registerInterceptor(int priority, IInterceptor interceptor) {
        if (mInterceptors == null) {
            mInterceptors = new SparseArray<>();
        }
        mInterceptors.put(priority, interceptor);
    }

    public synchronized void unregisterInterceptor(IInterceptor interceptor) {
        int index = mInterceptors.indexOfValue(interceptor);
        if (index < 0) {
            return;
        }
        mInterceptors.removeAt(index);
    }

    private Object navigateService(Context context, Class<?> clazz, ServiceInstantCallback instantCallback) {
        ServiceInstantCallback callback = instantCallback;
        try {
            return Trustee.instance().instanceComponentService(context, clazz);
        } catch (IllegalAccessException e) {
            if (callback != null) {
                callback.onError(e);
            }
            e.printStackTrace();
        } catch (InstantiationException e) {
            if (callback != null) {
                callback.onError(e);
            }
            e.printStackTrace();
        } catch (ComponentServiceInstantException e) {
            if (callback != null) {
                callback.onError(e);
            }
            e.printStackTrace();
        }
        return null;
    }

    Object navigate(int requestCode, Context context, Navigator guide, NavigateCallback navigateCallback) {
        RouterNode node = Trustee.instance().getRouterNode(guide.path);
        NavigateCallback callback = navigateCallback == null ? mNavigateCallback : navigateCallback;
        if (node == null) {
            if (callback != null) {
                callback.onLost(guide.path);
            }
            return null;
        }
        if (callback != null) {
            callback.onFound(node);
        }
        if (context == null) {
            context = sContext;
        }
        RealChain realChain = new RealChain(node);
        List<IInterceptor> interceptors = interceptorList();
        for (IInterceptor interceptor : interceptors) {
            if (!realChain.proceed) {
                return null;
            }
            interceptor.intercept(realChain);
        }

        switch (node.getType()) {
            case ACTIVITY:
                startActivity(requestCode, context, node, guide);
                break;
            case FRAGMENT:
                try {
                    return getFragmentInstance((FragmentNode) node, guide);
                } catch (InstantiationException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                }
                return null;
            case COMPONENT_SERVICE:
                try {
                    Trustee.instance().instanceComponentService(context, (ComponentServiceNode) node);
                } catch (InstantiationException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    if (callback != null) {
                        callback.onError(node, e);
                    }
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }
        return null;
    }

    private Fragment getFragmentInstance(FragmentNode routerNode, Navigator guide) throws InstantiationException, IllegalAccessException {
        Fragment fragment = (Fragment) routerNode.getTarget().newInstance();
        fragment.setArguments(guide.bundle);
        return fragment;
    }

    private IComponentService getComponentServiceInstance(Context context, ComponentServiceNode node, Navigator guide) throws InstantiationException, IllegalAccessException {
        return Trustee.instance().instanceComponentService(context, node);
    }

    private void startActivity(int requestCode, Context context, RouterNode node, Navigator guide) {
        Intent intent = new Intent(context, node.getTarget());
        if (guide.flags != 0) {
            intent.setFlags(guide.flags);
        }
        intent.putExtras(guide.bundle);
        if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        if (context instanceof Activity && guide.startForResult) {
            ((Activity) context).startActivityForResult(intent, requestCode);
        } else {
            context.startActivity(intent);
        }
    }

    private List<IInterceptor> interceptorList() {
        if (mInterceptorList == null) {
            mInterceptorList = new ArrayList<>();
        } else {
            mInterceptorList.clear();
        }
        if (mInterceptors == null) {
            return mInterceptorList;
        }
        int len = mInterceptors.size();
        for (int i = len - 1; i >= 0; i--) {
            int key = mInterceptors.keyAt(i);
            mInterceptorList.add(mInterceptors.get(key));
        }
        return mInterceptorList;
    }

    private static class RealChain implements IInterceptor.Chain {
        boolean proceed = true;
        RouterNode mNode;

        RealChain(RouterNode node) {
            mNode = node;
        }

        @Override
        public RouterNode node() {
            proceed = false;
            return mNode;
        }

        @Override
        public void proceed(RouterNode node) {
            if (node != mNode) {
                mNode = node;
            }
            proceed = true;
        }
    }
}
