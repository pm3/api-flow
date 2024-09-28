package eu.aston.user;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces
@Singleton
@Requires(classes = {AuthException.class, ExceptionHandler.class})
public class AuthExceptionHandler implements ExceptionHandler<AuthException, HttpResponse<Object>> {

    @Override
    public HttpResponse<Object> handle(HttpRequest request, AuthException e) {
        if (!e.isForbidden()) {
            return HttpResponseFactory.INSTANCE.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + e.getMessage() + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                    .body("<html><body><h1>Authorization Required</h1></body></html>");
        }
        return HttpResponseFactory.INSTANCE.status(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .body("{\"error\":\"forbidden\"}");
    }

}
