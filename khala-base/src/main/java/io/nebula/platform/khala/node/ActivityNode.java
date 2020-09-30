package io.nebula.platform.khala.node;

/**
 * Created by nebula on 2019-07-21
 */
public class ActivityNode extends RouterNode {

    public ActivityNode(String path, Class<?> target) {
        super(path, target);
    }

    @Override
    public NodeType getType() {
        return NodeType.ACTIVITY;
    }
}
