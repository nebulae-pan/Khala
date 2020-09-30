package io.nebula.platform.khala.node;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:14
 */
public class RouterNodeFactory {
    public static RouterNode produceRouterNode(NodeType type, String path, Class<?> target) {
        switch (type) {
            case ACTIVITY:
                return new ActivityNode(path, target);
            case FRAGMENT:
                return new FragmentNode(path, target);
            case COMPONENT_SERVICE:
                return new ComponentServiceNode(path, target);
        }
        return new UnspecifiedNode(path, target);
    }
}
