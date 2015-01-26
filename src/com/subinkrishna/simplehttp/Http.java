package com.subinkrishna.simplehttp;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A simple HTTP Utility.
 *
 * @author Subinkrishna Gopi
 */
public class Http {

    /** Maximum number of supported redirections */
    private static final int MAX_REDIRECTIONS = 5;

    /** Supported request types */
    private static enum RequestType { GET, POST, DELETE }

    public static Request get(String url) {
        return Request.newInstance(url, RequestType.GET);
    }

    public static Request post(String url) {
        return Request.newInstance(url, RequestType.POST);
    }

    public static Request delete(String url) {
        return Request.newInstance(url, RequestType.DELETE);
    }

    /**
     * HTTP(s) Request holder.
     *
     * @author Subinkrishna Gopi
     */
    public static class Request {

        /**
         * Creates a new Request instance with a URL.
         *
         * @param url
         * @return
         */
        static Request newInstance(String url,
                                   RequestType type) {
            Request request = new Request();
            request.mUrl = url;
            request.mRequestType = type;
            return request;
        }

        private String mUrl;
        private RequestType mRequestType;
        private boolean mDoRedirect = true;
        private List<Pair> mQueryParams;
        private List<Pair> mHeaders;
        private String mContent;
        private String mContentType;
        private String mUserAgent;
        private boolean mShowLogs = false;

        public Request query(Pair... params) {
            for(Pair aPair : params) {
                if ((null == aPair) || (null == aPair.mKey) || (0 == aPair.mKey.length())) {
                    continue;
                }
                mQueryParams = (null == mQueryParams) ? new LinkedList<Pair>() : mQueryParams;
                mQueryParams.add(aPair);
            }
            return this;
        }

        public Request query(String key, String value) {
            return query(Pair.of(key, value));
        }

        public Request header(Pair...params) {
            for(Pair aPair : params) {
                if ((null == aPair) || (null == aPair.mKey) || (0 == aPair.mKey.length())) {
                    continue;
                }
                mHeaders = (null == mHeaders) ? new LinkedList<Pair>() : mHeaders;
                mHeaders.add(aPair);
            }
            return this;
        }

        public Request header(String key, String value) {
            return header(Pair.of(key, value));
        }

        public Request userAgent(String userAgent) {
            header(Pair.of("User-Agent", userAgent));
            mUserAgent = userAgent;
            return this;
        }

        public Request noRedirect() {
            mDoRedirect = false;
            return this;
        }

        public Request verbose() {
            mShowLogs = true;
            return this;
        }

        public Request body(String content, String type) {
            mContent = content;
            mContentType = type;
            return this;
        }

        public Response asIs() {
            // Abort if the URL is invalid
            if ((null == mUrl) ||
                (0 == mUrl.trim().length())) {
                throwIt (new IllegalArgumentException("Invalid URL"));
            }

            // Set default CookieHandler if one not set yet
            setCookieHandlerIfNeeded();

            Response response = null;

            try {
                HttpURLConnection connection = toUrlConnection(mRequestType,
                        mUrl, mQueryParams, mDoRedirect);

                byte[] requestBody = null;
                String contentType = null;

                // Write post data
                if ((RequestType.POST == mRequestType) ||
                    (RequestType.DELETE == mRequestType)){
                    // RAW body
                    if (null != mContent) {
                        requestBody = mContent.getBytes();
                        contentType = mContentType;
                    }
                    // Query string
                    else {
                        String queryString = prepareQueryString(mQueryParams);
                        requestBody = ((null != queryString) && (queryString.trim().length() > 0))
                                ? queryString.getBytes() : null;
                        contentType = "application/x-www-form-urlencoded; charset=UTF-8";
                    }

                    // Add additional headers for POST
                    header(Pair.of("Content-Type", contentType),
                           Pair.of("Content-Length", String.valueOf(requestBody.length)));
                }

                // Set headers
                if (null != mHeaders) {
                    for (Pair aHeader : mHeaders) {
                        connection.setRequestProperty(aHeader.mKey.trim(),
                                String.valueOf(aHeader.mValue).trim());
                        log(String.format("%s: %s", aHeader.mKey, aHeader.mValue));
                    }
                }

                // Write request data
                if (null != requestBody) {
                    log(new String(requestBody));
                    connection.setDoOutput(true);
                    copy(new ByteArrayInputStream(requestBody), connection.getOutputStream(), true);
                }

                connection.connect();

                // Follow redirects if asked to do so
                String location = null;
                int redirectCount = 0;
                while (mDoRedirect &&
                       (++redirectCount <= MAX_REDIRECTIONS) &&
                       (3 == (connection.getResponseCode() / 100)) &&
                       (null != (location = connection.getHeaderField("Location")))) {
                    response = Response.from(connection);
                    log(response);
                    log(String.format("\nRedirect #%d (%s)", redirectCount, location));

                    // Disconnect existing connection
                    connection.disconnect();
                    connection = toUrlConnection(RequestType.GET, location, null, true);
                    // Set User-Agent if explicitly specified
                    if (null != mUserAgent) {
                        connection.setRequestProperty("User-Agent", mUserAgent);
                    }
                    connection.connect();
                }

                if (redirectCount > MAX_REDIRECTIONS) {
                    throw new IllegalStateException("Too many redirections");
                }

                response = Response.from(connection);
                log(response);

            } catch (Throwable t) {
                response = null;
                throwIt(t);
            }

            return response;
        }

        public byte[] asBytes() {
            Response response = asIs();
            return (null != response) ? response.mBody : null;
        }

        public String asString() {
            byte[] bytes = asBytes();
            return (null != bytes) ? new String(bytes) : null;
        }

        public <T> T map(ResponseMapper<T> mapper) {
            if (null == mapper) {
                throw new IllegalArgumentException("ResponseMapper & type cannot be NULL");
            }
            return mapper.map(asIs());
        }

        private  HttpURLConnection toUrlConnection(final RequestType requestType,
                                                   final String url,
                                                   final List<Pair> queryParams,
                                                   final boolean redirect) {
            HttpURLConnection connection = null;

            try {
                String urlString = url;
                // Append query string for GET
                String queryString = null;
                if ((RequestType.GET == requestType) &&
                        (null != queryParams) &&
                        (null != (queryString = prepareQueryString(queryParams))) &&
                        (queryString.length() > 0)) {

                    final char joinChar = url.contains("?") ? '&' : '?';
                    urlString = String.format("%s%c%s", url, joinChar, queryString);
                }

                log(String.format("HTTP %s %s", requestType.toString(), urlString));

                connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setDoInput(true);
                connection.setRequestMethod(requestType.toString());
                connection.setInstanceFollowRedirects(redirect);

            } catch (Throwable t) {
                throwIt(t);
            }

            return connection;
        }

        private void log(Object object) {
            if (mShowLogs && (null != object)) {
                System.out.println(String.valueOf(object));
            }
        }
    }

    /**
     * HTTP(s) Response Holder
     *
     * @author Subinkrishna Gopi
     */
    public static class Response {

        public static Response from(HttpURLConnection connection) {
            Response response = null;
            ByteArrayOutputStream to = null;

            if (null != connection) {
                try {
                    // Read data
                    to = new ByteArrayOutputStream();

                    // Build response
                    response = new Response();
                    response.mResponseCode = connection.getResponseCode();
                    response.mResponseHeaders = connection.getHeaderFields();
                    response.mResponseMessage = connection.getResponseMessage();

                    // Switch streams based on response code
                    InputStream from = (4 == (response.mResponseCode / 100))
                            ? connection.getErrorStream()
                            : connection.getInputStream();
                    copy(from, to, true);
                    response.mBody = to.toByteArray();
                } catch (Throwable e) {
                    throwIt(e);
                } finally {
                    to = null;
                    connection.disconnect();
                }
            }

            return response;
        }

        public int mResponseCode;
        public String mResponseMessage;
        public Map<String, List<String>> mResponseHeaders;
        public byte[] mBody;

        @Override
        public String toString() {
            final StringBuilder responseBuilder = new StringBuilder();
            for (String key : mResponseHeaders.keySet()) {
                if (0 == responseBuilder.length()) {
                    responseBuilder.append("\n");
                }

                if (null != key) {
                    responseBuilder.append(key).append(": ");
                }

                responseBuilder.append(mResponseHeaders.get(key).toString()).append("\n");
            }
            return responseBuilder.toString();
        }
    }

    /**
     * A simple key-value pair.
     *
     * @author Subinkrishna Gopi
     */
    public static class Pair {

        public static Pair of(String key, Object value) {
            if ((null == key) || (0 == key.trim().length())) {
                return null;
            }

            Pair aPair = new Pair();
            aPair.mKey = key;
            aPair.mValue = value;
            return aPair;
        }

        public String mKey;
        public Object mValue;

        public String toQueryString() {
            String queryString = null;
            try {
                queryString = String.format("%s=%s",
                        URLEncoder.encode(mKey, "UTF-8").trim(),
                        URLEncoder.encode(String.valueOf(mValue), "UTF-8").trim());
            } catch (Throwable t) {
                queryString = "";
            }
            return queryString;
        }

    }

    private static void setCookieHandlerIfNeeded() {
        CookieHandler defaultHandler = CookieHandler.getDefault();
        if (null == defaultHandler) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    private static String prepareQueryString(final List<Pair> queryParams) {
        if (null != queryParams) {
            StringBuilder queryBuilder = new StringBuilder();
            for (Pair aParam : queryParams) {
                try {
                    queryBuilder.append((queryBuilder.length() > 0) ? "&" : "");
                    queryBuilder.append(aParam.toQueryString());
                } catch (Throwable t) {
                }
            }
            return queryBuilder.toString();
        }
        return "";
    }

    public static void copy(final InputStream from,
                            final OutputStream to,
                            final boolean closeStreams) throws IOException {
        final int kilobyte = 1024;
        byte[] buffer = null;
        int count = -1;

        if ((null != from) && (null != to)) {
            try {
                buffer = new byte[kilobyte];
                while (-1 != (count = from.read(buffer))) {
                    to.write(buffer, 0, count);
                }
            } finally {
                buffer = null;
                // Close streams
                if (closeStreams) {
                    try {
                        from.close();
                        to.close();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
    }

    private static void throwIt(Throwable t) {
        RuntimeException re = (t instanceof RuntimeException)
                ? (RuntimeException) t : new RuntimeException(t);
        throw re;
    }
}
