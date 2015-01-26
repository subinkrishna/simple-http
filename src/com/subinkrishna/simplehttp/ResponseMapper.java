package com.subinkrishna.simplehttp;

/**
 * @author Subinkrishna Gopi
 */
public interface ResponseMapper<T> {
    public T map(final Http.Response response);
}
