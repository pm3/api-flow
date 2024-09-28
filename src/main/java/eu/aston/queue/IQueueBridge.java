package eu.aston.queue;

import java.util.Map;

public interface IQueueBridge {
    void queueEventSent(String id);

    boolean eventResponse(QueueEvent event, int status, Map<String, String> headers, byte[] data);
}
