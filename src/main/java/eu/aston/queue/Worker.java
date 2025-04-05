package eu.aston.queue;

import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpResponse;

public class Worker {
    private final String id;
    private final String prefix;
    private final int timeout;
    private CompletableFuture<HttpResponse<?>> response;
    public final long created = System.currentTimeMillis();
    private final int slow;

    public Worker(String id, String prefix, int timeout, CompletableFuture<HttpResponse<?>> response) {
        this.id = id;
        this.timeout = timeout;
        this.response = response;
        if(prefix.matches("^.+@slow[0-9]+$")) {
            this.prefix = prefix.substring(0, prefix.indexOf("@"));
            this.slow = Integer.parseInt(prefix.substring(prefix.lastIndexOf("@slow")+5));
        } else {
            this.prefix = prefix;
            this.slow = 0;
        }
    }

    public String getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getSlow() {
        return slow;
    }

    public synchronized CompletableFuture<HttpResponse<?>> removeResponse() {
        var r = this.response;
        this.response = null;
        return r;
    }
}
