package eu.aston.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import eu.aston.AppConfig;
import eu.aston.header.HeaderConverter;
import eu.aston.queue.QueueEvent;
import eu.aston.queue.QueueStore;
import eu.aston.queue.Worker;
import eu.aston.utils.ID;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;

@Controller
public class QueueController {

    private final QueueStore queueStore;
    private final String workerApiKey;

    public QueueController(QueueStore queueStore, AppConfig appConfig) {
        this.queueStore = queueStore;
        this.workerApiKey = appConfig.getWorkerApiKey()!=null && !appConfig.getWorkerApiKey().isEmpty() ? appConfig.getWorkerApiKey() : null;
    }

    @Get(value = "/queue/{path:.*}", processes = MediaType.ALL)
    @Post(value = "/queue/{path:.*}", processes = MediaType.ALL)
    @Put(value = "/queue/{path:.*}", processes = MediaType.ALL)
    @Delete(value = "/queue/{path:.*}", processes = MediaType.ALL)
    public CompletableFuture<HttpResponse<Object>> send(HttpRequest<byte[]> request, @PathVariable String path, @Nullable @QueryValue("timeout") Integer timeout){
        CompletableFuture<HttpResponse<Object>> future = new CompletableFuture<>();
        QueueEvent event = new QueueEvent();
        event.setId(ID.newId());
        event.setMethod(request.getMethodName());
        event.setPath(request.getUri().getPath());
        event.setHeaders(HeaderConverter.eventRequest(request.getHeaders(), event.getId(), event.getMethod(), event.getPath()));
        event.setBody(request.getBody().orElse(null));
        event.setCallback(HeaderConverter.createCallback(request.getHeaders()));
        if(timeout!=null && timeout>0) {
            event.setWaitingWriter(future);
        } else {
            future.complete(HttpResponse.status(201, "accepted").header(HeaderConverter.H_ID, event.getId()));
        }
        return future;
    }

    @Get(value = "/.queue/worker")
    public CompletableFuture<HttpResponse<?>> workerConsume(HttpRequest<byte[]> request, @QueryValue String path, @QueryValue String workerId) {
        if(workerApiKey!=null && !Objects.equals(workerApiKey, request.getHeaders().getFirst("X-Api-Key").orElse(null))){
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "require X-Api-Key");
        }
        CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>();
        Worker worker = new Worker(path, 30, future);
        queueStore.workerQueue(worker);
        return future;
    }

    @Post(value = "/.queue/response/{eventId}")
    public void workerResponse(HttpRequest<byte[]> request, @PathVariable String eventId) {
        if(workerApiKey!=null && !Objects.equals(workerApiKey, request.getHeaders().getFirst("X-Api-Key").orElse(null))){
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "require X-Api-Key");
        }
        int status = request.getHeaders().getInt(HeaderConverter.H_STATUS);
        var headers = HeaderConverter.eventResponse(request.getHeaders(), eventId);
        queueStore.response(eventId, status, headers, request.getBody().orElse(new byte[0]));
    }
}
