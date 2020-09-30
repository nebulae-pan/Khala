package io.nebula.platform.khala.exception;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-25 15:13
 */
public class HashCollisionException extends RuntimeException{
    public HashCollisionException(String path, String collision) {
        super("Query path:" + path + ", but found routerNode path:" + collision);
    }
}
