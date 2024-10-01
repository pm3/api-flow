package eu.aston.controller;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import eu.aston.AppConfig;
import eu.aston.header.HeaderConverter;
import eu.aston.queue.EventResponse;
import eu.aston.queue.QueueEvent;
import eu.aston.queue.QueueStore;
import eu.aston.queue.Worker;
import eu.aston.utils.ID;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class QueueController {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueController.class);

    private final QueueStore queueStore;
    private final String workerApiKey;

    public QueueController(QueueStore queueStore, AppConfig appConfig) {
        this.queueStore = queueStore;
        this.workerApiKey = appConfig.getWorkerApiKey()!=null && !appConfig.getWorkerApiKey().isEmpty() ? appConfig.getWorkerApiKey() : null;
    }

    @Post(value = "/queue/{path:.*}", processes = MediaType.ALL)
    public CompletableFuture<HttpResponse<Object>> send(HttpRequest<byte[]> request, @PathVariable("path") String path, @Nullable @QueryValue("timeout") Integer timeout){
        LOGGER.info("send /{}", path);
        CompletableFuture<HttpResponse<Object>> future = new CompletableFuture<>();
        QueueEvent event = new QueueEvent();
        event.setId(ID.newId());
        event.setMethod(request.getMethodName());
        event.setPath("/"+path);
        event.setHeaders(HeaderConverter.eventRequest(request.getHeaders(), event.getId(), event.getMethod(), event.getPath()));
        event.setBody(request.getBody().orElse(null));
        event.setCallback(HeaderConverter.createCallback(request.getHeaders()));
        if(timeout!=null && timeout>0) {
            if(timeout>45) timeout=45;
            HandleWaitingResponse handleWaitingResponse = new HandleWaitingResponse(event, future);
            event.setHandleResponse(handleWaitingResponse::send);
            queueStore.getSuperTimer().schedule(timeout*1000L, handleWaitingResponse::timeout);
        } else {
            future.complete(HttpResponse.status(201, "accepted").header(HeaderConverter.H_ID, event.getId()));
        }
        queueStore.addEvent(event);
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
        String requestApiKey = request.getHeaders().getFirst("X-Api-Key").orElse(null);
        if(workerApiKey!=null && !Objects.equals(workerApiKey, requestApiKey)){
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "require X-Api-Key");
        }
        int status = request.getHeaders().getInt(HeaderConverter.H_STATUS);
        var headers = HeaderConverter.eventResponse(request.getHeaders(), eventId);
        queueStore.response(eventId, status, headers, request.getBody().orElse(new byte[0]));
    }

    public static class HandleWaitingResponse {
        private final QueueEvent event;
        private CompletableFuture<HttpResponse<Object>> future;

        public HandleWaitingResponse(QueueEvent event, CompletableFuture<HttpResponse<Object>> future) {
            this.event = event;
            this.future = future;
        }

        private synchronized CompletableFuture<HttpResponse<Object>> removeFuture(){
            CompletableFuture<HttpResponse<Object>> future2 = this.future;
            this.future = null;
            return future2;
        }

        public void send(EventResponse eventResponse){
            CompletableFuture<HttpResponse<Object>> future2 = removeFuture();
            if(future2!=null){
                try{
                    future2.complete(HttpResponse.status(HttpStatus.valueOf(eventResponse.status()))
                                             .headers(new HashMap<>(eventResponse.headers()))
                                             .body(eventResponse.body())
                                    );
                    event.setCallback(null);
                }catch (Exception ignore){
                }
            }
        }

        public void timeout() {
            CompletableFuture<HttpResponse<Object>> future2 = removeFuture();
            if(future2!=null) {
                future2.complete(HttpResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                                       .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                                       .body("<h1>Queue Gateway Timeout</h1>"));

            }
        }
    }
}
