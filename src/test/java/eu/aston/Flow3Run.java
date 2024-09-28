package eu.aston;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;

public class Flow3Run {
    public static void main(String[] args) {

        try{
            Executor executor = Executors.newThreadPerTaskExecutor(Thread::new);
            executor.execute(()->SumServer.main(new String[]{}));
            executor.execute(()->Application.main(new String[]{}));

            HttpClient httpClient = HttpClient.newBuilder().build();
            ObjectMapper objectMapper = new ObjectMapper();

            Map<String,Object> map = new HashMap<>();
            map.put("a", 2);
            map.put("b", 3);
            map.put("c", List.of("a", "b", "c"));
            String json = objectMapper.writeValueAsString(map);

            Thread.sleep(200);
            HttpRequest r = HttpRequest
                    .newBuilder()
                    .uri(new URI("http://localhost:8080/flow/start/flow1"))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .build();
            HttpResponse<String> resp = httpClient.send(r, HttpResponse.BodyHandlers.ofString());
            System.out.println(resp.statusCode()+" "+resp.body());
            resp.headers().map().forEach((k,v)-> System.out.println(k+": "+v.getFirst()));

        }catch (Exception e){
            e.printStackTrace();
        }


    }
}
