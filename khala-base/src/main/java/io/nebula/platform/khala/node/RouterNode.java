package io.nebula.platform.khala.node;

import android.os.Bundle;
import android.text.TextUtils;

/**
 * Created by nebula on 2019-07-21
 */
@SuppressWarnings("WeakerAccess")
public abstract class RouterNode {
    protected String mSchema;
    protected String mGroup;
    protected String mPath;
    protected NodeType mType;
    protected Class<?> mTarget;
    protected Bundle mBundle;

    public RouterNode(String path, Class<?> target) {
        mPath = path;
        mTarget = target;
        mType = getType();
    }

    public RouterNode(String path, String group) {
        mPath = path;
        mGroup = group;
    }

    public abstract NodeType getType();

    public String getPath() {
        return mPath;
    }

    public Class<?> getTarget() {
        return mTarget;
    }

    private boolean isPathValid() {
        if (TextUtils.isEmpty(mPath)) {
        }
        return false;
    }
}
