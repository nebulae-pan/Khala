package io.nebula.platform.khala.node;

import java.util.Set;

import io.nebula.platform.khala.util.CollectionHelper;


/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:20
 */
public enum NodeType {
    ACTIVITY(CollectionHelper.setOf("android.app.Activity")),
    FRAGMENT(CollectionHelper.setOf("androidx.fragment.app.Fragment",
            "android.app.Fragment",
            "android.support.v4.app.Fragment")),
    COMPONENT_SERVICE(CollectionHelper.setOf("io.nebula.platform.khala.IComponentService")),
    UNSPECIFIED(CollectionHelper.setOf(""));

    NodeType(Set<String> supportClasses) {
        this.supportClasses = supportClasses;
    }

    private Set<String> supportClasses;

    public Set<String> supportClasses() {
        return supportClasses;
    }
}
