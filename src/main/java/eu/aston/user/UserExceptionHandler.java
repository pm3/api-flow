package eu.aston.user;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
@Requires(classes = {UserException.class, ExceptionHandler.class})
public class UserExceptionHandler implements ExceptionHandler<UserException, HttpResponse<Object>> {

    @Override
    public HttpResponse<Object> handle(HttpRequest request, UserException e) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("message", e.getMessage());
        root.put("path", request.getPath());
        root.put("status", HttpStatus.BAD_REQUEST.getCode());
        root.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        return HttpResponseFactory.INSTANCE.status(HttpStatus.BAD_REQUEST).body(root.toString());
    }

}