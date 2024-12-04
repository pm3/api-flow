package eu.aston.flow;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.header.HeaderConverter;
import eu.aston.queue.EventResponse;
import eu.aston.queue.QueueEvent;
import eu.aston.queue.QueueStore;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QueueFlowBridge {

    private final static Logger LOGGER = LoggerFactory.getLogger(QueueFlowBridge.class);
    public static final String QUEUE_PREFIX = "/queue/";

    private final QueueStore queueStore;
    private final ObjectMapper objectMapper;

    public QueueFlowBridge(QueueStore queueStore, ObjectMapper objectMapper) {
        this.queueStore = queueStore;
        this.objectMapper = objectMapper;
    }

    public boolean queueEvent(String path){
        return queueStore!=null && path.startsWith(QUEUE_PREFIX) && path.length()> QUEUE_PREFIX.length();
    }

    public void sendQueueEvent(FlowTaskEntity task, TaskHttpRequest request, Runnable handleSend, BiConsumer<Integer, Object> finishTask){
        if(queueEvent(request.path())){
            String path = request.path().substring(QUEUE_PREFIX.length()-1);
            LOGGER.info("event {} <= {}/{} - {}", path, task.getFlowCaseId(), task.getId(), task.getWorker());
            Map<String, String> headers = new HashMap<>();
            if(request.headers()!=null) headers.putAll(request.headers());
            QueueEvent event = new QueueEvent();
            event.setId(task.getId());
            event.setPath(path);
            event.setMethod(request.method());
            event.setHeaders(headers);
            headers.put(HeaderConverter.H_CASE_ID, task.getFlowCaseId());
            headers.put(HeaderConverter.H_ID, task.getId());
            headers.put(HeaderConverter.H_METHOD, event.getMethod());
            headers.put(HeaderConverter.H_URI, path);
            if(!headers.containsKey(HttpHeaders.CONTENT_TYPE)) headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            event.setBody(request.body().getBytes(StandardCharsets.UTF_8));
            event.setHandleSend(handleSend);
            event.setHandleResponse((eventResponse -> eventResponse(finishTask, eventResponse)));
            queueStore.addEvent(event);
        }
    }

    public void eventResponse(BiConsumer<Integer, Object> finishTask, EventResponse eventResponse) {
        try {
            if(eventResponse.status()>=200 && eventResponse.status()<300) {
                Object root = objectMapper.readValue(eventResponse.body(), Object.class);
                finishTask.accept(eventResponse.status(), root);
            } else {
                finishTask.accept(eventResponse.status(), new String(eventResponse.body(), StandardCharsets.UTF_8));
            }
        }catch (Exception e){
            finishTask.accept(400, "parse json body error "+e.getMessage());
        }
    }
}
