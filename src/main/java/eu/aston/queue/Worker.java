package eu.aston.queue;

import java.util.concurrent.CompletableFuture;

import io.micronaut.http.HttpResponse;

public class Worker {
    private final String prefix;
    private final int timeout;
    private CompletableFuture<HttpResponse<?>> response;

    public Worker(String prefix, int timeout, CompletableFuture<HttpResponse<?>> response) {
        this.prefix = prefix;
        this.timeout = timeout;
        this.response = response;
    }

    public String getPrefix() {
        return prefix;
    }

    public int getTimeout() {
        return timeout;
    }
    
    public synchronized CompletableFuture<HttpResponse<?>> removeResponse() {
        var r = this.response;
        this.response = null;
        return r;
    }
}
