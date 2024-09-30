package eu.aston.header;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.utils.SuperTimer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CallbackRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger("CALLBACK");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SuperTimer superTimer;

    public CallbackRunner(HttpClient httpClient, ObjectMapper objectMapper, SuperTimer superTimer) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.superTimer = superTimer;
    }

    public <T> HttpResponse<T> call(String method, URI uri, Map<String, String> headers, Object body, HttpResponse.BodyHandler<T> bodyHandler) throws Exception{
        HttpRequest.Builder b = HttpRequest.newBuilder(uri);
        if(headers!=null) headers.forEach(b::header);
        b.method(method, createBodyPublisher(body));
        return httpClient.send(b.build(), bodyHandler);
    }

    public <T> CompletableFuture<HttpResponse<T>> callAsync(String method, URI uri, Map<String, String> headers, Object body, HttpResponse.BodyHandler<T> bodyHandler){
        CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
        superTimer.execute(()->{
            try{
                HttpResponse<T> response = call(method, uri, headers, body, bodyHandler);
                LOGGER.debug("send {} {} response {}", method, uri, response.statusCode());
                future.complete(response);
            }catch (Exception e){
                LOGGER.warn("call {} {} response error {}", method, uri, e.getMessage());
                LOGGER.debug("call {} {} response trace", method, uri, e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void callbackAsync(String eventId, Callback callback, Map<String, String> headers, Object body){
        superTimer.execute(()-> callbackBlocked(eventId, callback, headers, body));
    }

    public void callbackBlocked(String eventId, Callback callback, Map<String, String> headers, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(new URI(callback.url()));
            if(callback.headers()!=null) callback.headers().forEach(b::header);
            if(headers!=null) headers.forEach(b::header);
            b.POST(createBodyPublisher(body));
            HttpResponse<byte[]> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            LOGGER.debug("{} - {} status {}", eventId, callback.url(), resp.statusCode());
        } catch (Exception e) {
            LOGGER.debug("{} - {} error {}", eventId, callback.url(), e.getMessage());
        }
    }

    private HttpRequest.BodyPublisher createBodyPublisher(Object body) throws JsonProcessingException {
        if(body instanceof String s){
            return HttpRequest.BodyPublishers.ofString(s);
        } else if(body instanceof byte[] bytea){
            return HttpRequest.BodyPublishers.ofByteArray(bytea);
        } else if(body!=null){
            return HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(body));
        }
        return HttpRequest.BodyPublishers.noBody();
    }
}
