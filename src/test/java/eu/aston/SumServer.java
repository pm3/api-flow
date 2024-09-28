package eu.aston;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.aston.header.HeaderConverter;
import eu.aston.user.UserException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;

public class SumServer implements HttpHandler {

    public static void main(String[] args) {
        try {

            SumServer workerServer = new SumServer(null);
            Executor executor = Executors.newSingleThreadExecutor();
            executor.execute(workerServer::worker);

            Executor executor2 = Executors.newSingleThreadExecutor();
            int port = 8081;
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", new SumServer(executor2));
            httpServer.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final Executor executor;

    public SumServer(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println(exchange.getRequestMethod()+" "+exchange.getRequestURI());
        exchange.getRequestHeaders().forEach((k,v)-> System.out.println(k+": "+v.getFirst()));
        byte[] body = exchange.getRequestBody().readAllBytes();
        byte[] respBody = processRequest(body);
        String callback = exchange.getRequestHeaders().getFirst(HeaderConverter.H_CALLBACK_URL);
        if (callback != null) {
            exchange.sendResponseHeaders(201, 0);
            exchange.getResponseBody().close();
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((k,v)->{
                if(k.toLowerCase().startsWith(HeaderConverter.H_CALLBACK_PREFIX)) headers.put(k.substring(12), v.getFirst());
            });
            executor.execute(()->sendCallback(callback, headers, respBody));
        } else {
            exchange.sendResponseHeaders(200, respBody.length);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            exchange.getResponseBody().write(respBody);
            exchange.getResponseBody().close();
            System.out.println("send blocked "+new String(respBody));
        }
    }

    private byte[] processRequest(byte[] body) throws JsonProcessingException {
        Map<String, Object> params = null;
        Map<String, Object> resp = new HashMap<>();
        if(body.length>0){
            try{
                MapType type = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
                params = objectMapper.readValue(body, type);
            }catch (Exception e){
                throw new UserException("params not cast to json object");
            }
        }

        int sum = 0;
        if(params!=null){
            System.out.println("params "+params);
            resp.putAll(params);
            for(Map.Entry<String, Object> e : params.entrySet()){
                if(e.getValue() instanceof Number n) {
                    System.out.println("add "+e.getKey()+" "+n);
                    sum+=n.intValue();
                }
                if(e.getValue() instanceof String s) {
                    try{
                        int n = Integer.parseInt(s);
                        System.out.println("add "+e.getKey()+" "+n);
                        sum+=n;
                    }catch (Exception ignore){}
                }
            }
        }
        resp.put("c", sum);
        byte[] respBody = objectMapper.writeValueAsBytes(resp);
        return respBody;
    }

    private void sendCallback(String callback, Map<String, String> headers, byte[] respBody) {
        try{
            HttpRequest.Builder b = HttpRequest.newBuilder();
            b.uri(new URI(callback));
            b.POST(HttpRequest.BodyPublishers.ofByteArray(respBody));
            b.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            headers.forEach(b::header);
            HttpResponse<Void> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.discarding());
            System.out.println("send callback "+new String(respBody));
            System.out.println("callback response "+resp.statusCode());
        }catch (Exception e){
            System.out.println("callback error "+e.getMessage());
        }
    }

    public void worker(){
        while (!Thread.interrupted()){
            try{
                workerStep();
            }catch (Exception e){
                System.out.println("exception "+e.getMessage());
                try{
                    Thread.sleep(1000);
                }catch (Exception e2){}
            }
        }
    }

    public void workerStep() throws Exception {
        System.out.println("call worker");
        HttpRequest r = HttpRequest.newBuilder()
                .GET()
                .uri(new URI("http://localhost:8080/.queue/worker?path=/queue/sum"))
                .build();
        HttpResponse<byte[]> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofByteArray());
        System.out.println(r.uri()+" "+resp.statusCode());
        resp.headers().map().forEach((k,v)-> System.out.println(k+": "+v.getFirst()));

        if(resp.statusCode()==200 && resp.body().length>0) {
            byte[] body = resp.body();
            byte[] respBody = processRequest(body);
            String eventId = resp.headers().firstValue(HeaderConverter.H_ID).orElse(null);
            if(eventId!=null){
                String callback = "http://localhost:8080/.queue/response/"+eventId;
                sendCallback(callback, Map.of(HeaderConverter.H_STATUS, "200"), respBody);
            }
        } else {
            Thread.sleep(500);
        }
    }
}
