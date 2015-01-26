package com.subinkrishna.simplehttp;

import com.google.gson.Gson;

/**
 * @author Subinkrishna Gopi
 */
public class JsonMapper<T> implements ResponseMapper<T> {

    private Class<T> mType;

    public static <T> JsonMapper<T> forType(Class<T> type) {
        JsonMapper<T> mapper = new JsonMapper<T>();
        mapper.mType = type;
        return mapper;
    }

    @Override
    public T map(Http.Response response) {
        T result = null;
        if (null != response) {
            Gson gson = new Gson();
            String json = new String(response.mBody);
            result = gson.fromJson(json, mType);
        }
        return result;
    }
}
