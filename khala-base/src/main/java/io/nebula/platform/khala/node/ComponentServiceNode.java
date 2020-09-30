package io.nebula.platform.khala.node;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 18:35
 */
public class ComponentServiceNode extends RouterNode {
    public ComponentServiceNode(String path, Class<?> target) {
        super(path, target);
    }

    @Override
    public NodeType getType() {
        return NodeType.COMPONENT_SERVICE;
    }
}
