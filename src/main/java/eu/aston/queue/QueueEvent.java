package eu.aston.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import eu.aston.header.Callback;

public class QueueEvent {
    private String id;

    private String method;
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private Callback callback;

    private long t1 = System.currentTimeMillis();
    private long t2;
    private long t3;

    private Runnable handleSend;
    private Consumer<EventResponse> handleResponse;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public long getT1() {
        return t1;
    }

    public void setT1(long t1) {
        this.t1 = t1;
    }

    public long getT2() {
        return t2;
    }

    public void setT2(long t2) {
        this.t2 = t2;
    }

    public long getT3() {
        return t3;
    }

    public void setT3(long t3) {
        this.t3 = t3;
    }

    public Runnable getHandleSend() {
        return handleSend;
    }

    public void setHandleSend(Runnable handleSend) {
        this.handleSend = handleSend;
    }

    public Consumer<EventResponse> getHandleResponse() {
        return handleResponse;
    }

    public void setHandleResponse(Consumer<EventResponse> handleResponse) {
        this.handleResponse = handleResponse;
    }
}
