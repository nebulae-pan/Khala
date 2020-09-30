package io.nebula.platform.khala;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-25 18:33
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class Navigator {
    String path;
    String group;
    Bundle bundle;

    //for activity
    int flags;
    boolean startForResult;

    public Navigator(String path) {
        this.path = path;
        init();
    }

    public Navigator(String path, String group) {
        this.path = path;
        this.group = group;
        init();
    }

    private void init() {
        bundle = new Bundle();
    }

    public Navigator withString(String key, String value) {
        bundle.putString(key, value);
        return this;
    }

    public Navigator withStringArray(String key, String[] value) {
        bundle.putStringArray(key, value);
        return this;
    }

    public Navigator withStringArrayList(String key, ArrayList<String> value) {
        bundle.putStringArrayList(key, value);
        return this;
    }

    public Navigator withSerializable(String key, Serializable serializable) {
        bundle.putSerializable(key, serializable);
        return this;
    }

    public Navigator withParcelable(String key, Parcelable value) {
        bundle.putParcelable(key, value);
        return this;
    }

    public Navigator withParcelableArray(String key, Parcelable[] value) {
        bundle.putParcelableArray(key, value);
        return this;
    }

    public Navigator withParcelableArrayList(String key, ArrayList<Parcelable> value) {
        bundle.putParcelableArrayList(key, value);
        return this;
    }


    public Navigator withInt(String key, int value) {
        bundle.putInt(key, value);
        return this;
    }

    public Navigator withIntArray(String key, int[] array) {
        bundle.putIntArray(key, array);
        return this;
    }

    public Navigator withIntegerArrayList(String key, ArrayList<Integer> value) {
        bundle.putIntegerArrayList(key, value);
        return this;
    }

    public Navigator withBoolean(String key, boolean value) {
        bundle.putBoolean(key, value);
        return this;
    }

    public Navigator withBooleanArray(String key, boolean[] value) {
        bundle.putBooleanArray(key, value);
        return this;
    }

    public Navigator withFloat(String key, float value) {
        bundle.putFloat(key, value);
        return this;
    }

    public Navigator withFloatArray(String key, float[] value) {
        bundle.putFloatArray(key, value);
        return this;
    }

    public Navigator withLong(String key, long value) {
        bundle.putLong(key, value);
        return this;
    }

    public Navigator withLongArray(String key, long[] array) {
        bundle.putLongArray(key, array);
        return this;
    }

    public Navigator withByte(String key, byte value) {
        bundle.putByte(key, value);
        return this;
    }

    public Navigator withByteArray(String key, byte[] value) {
        bundle.putByteArray(key, value);
        return this;
    }

    public Navigator withDouble(String key, double value) {
        bundle.putDouble(key, value);
        return this;
    }

    public Navigator withDoubleArray(String key, double[] value) {
        bundle.putDoubleArray(key, value);
        return this;
    }

    public Navigator withChar(String key, char value) {
        bundle.putChar(key, value);
        return this;
    }

    public Navigator withCharArray(String key, char[] value) {
        bundle.putCharArray(key, value);
        return this;
    }

    public Navigator withShort(String key, short value) {
        bundle.putShort(key, value);
        return this;
    }

    public Navigator withShortArray(String key, short[] value) {
        bundle.putShortArray(key, value);
        return this;
    }

    public Navigator withCharSequence(String key, CharSequence value) {
        bundle.putCharSequence(key, value);
        return this;
    }

    public Navigator withCharSequenceArray(String key, CharSequence[] value) {
        bundle.putCharSequenceArray(key, value);
        return this;
    }

    public Navigator withCharSequenceArrayList(String key, ArrayList<CharSequence> value) {
        bundle.putCharSequenceArrayList(key, value);
        return this;
    }

    public Navigator withFlags(int flags) {
        this.flags = flags;
        return this;
    }

    public Object navigate() {
        return navigate(null);
    }

    public Object navigate(Context context) {
        return navigateInternal(0, context, null);
    }

    public Object navigate(int requestCode, Activity context) {
        startForResult = true;
        return navigateInternal(requestCode, context, null);
    }

    public Object navigate(int requestCode, Activity context, KhalaRouter.NavigateCallback callback) {
        startForResult = true;
        return navigateInternal(requestCode, context, callback);
    }

    public Object navigate(Context context, KhalaRouter.NavigateCallback callback) {
        return navigateInternal(0, context, callback);
    }

    private Object navigateInternal(int requestCode, Context context, KhalaRouter.NavigateCallback navigateCallback) {
        return KhalaRouter.instance().navigate(requestCode, context, this, navigateCallback);
    }
}
