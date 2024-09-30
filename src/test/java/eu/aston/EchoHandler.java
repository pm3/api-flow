package eu.aston;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.aston.header.HeaderConverter;

public class EchoHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println(exchange.getRequestMethod()+" "+exchange.getRequestURI());
        for(String k : exchange.getRequestHeaders().keySet()){
            System.out.println(k+": "+exchange.getRequestHeaders().getFirst(k));
        }
        String callback = exchange.getRequestHeaders().getFirst(HeaderConverter.H_CALLBACK_URL);
        byte[] body = exchange.getRequestBody().readAllBytes();
        if(callback!=null){
            System.out.println(callback+" "+new String(body));
            exchange.sendResponseHeaders(201, 0L);
            exchange.getResponseBody().close();
        } else {
            System.out.println(new String(body));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        }
    }
}
