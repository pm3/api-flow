package eu.aston.queue;

import java.util.Map;

public record EventResponse(int status,
                            Map<String,String> headers,
                            byte[] body) {
}
