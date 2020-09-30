package io.nebula.platform.khala.util;

import android.util.SparseArray;

import java.util.HashSet;
import java.util.Set;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 16:27
 */
public class CollectionHelper {
    @SafeVarargs
    public static <T> Set<T> setOf(T... args) {
        Set<T> set = new HashSet<>();
        java.util.Collections.addAll(set, args);
        return set;
    }

    public static <T> void putAllSparseArray(SparseArray<T> source, SparseArray<T> dest) {
        int size = dest.size();
        for (int i = 0; i < size; i++) {
            source.put(dest.keyAt(i), dest.valueAt(i));
        }
    }
}
