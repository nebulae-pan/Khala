package io.nebula.platform.khala.exception;

/**
 * @author panxinghai
 * <p>
 * date : 2019-09-30 15:54
 */
public class InjectTypeException extends RuntimeException {
    public InjectTypeException(String target, String field, String type) {
        super("@Inject annotation type Error: got [" + target + "." + field + "] type is:" + type +
                ". @Inject must annotate the field which type is support by bundle.");
    }
}
