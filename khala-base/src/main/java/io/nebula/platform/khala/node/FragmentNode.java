package io.nebula.platform.khala.node;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-01 11:57
 */
public class FragmentNode extends RouterNode {
    public FragmentNode(String path, Class<?> target) {
        super(path, target);
    }

    @Override
    public NodeType getType() {
        return NodeType.FRAGMENT;
    }
}
