package eu.aston.queue;

import java.util.Map;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record EventResponse(int status,
                            Map<String,String> headers,
                            byte[] body) {
}
