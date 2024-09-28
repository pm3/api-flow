package eu.aston;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import eu.aston.flow.FlowCaseManager;
import eu.aston.flow.IFlowBridge;
import eu.aston.header.HeaderConverter;
import eu.aston.queue.IQueueBridge;
import eu.aston.queue.QueueEvent;
import eu.aston.queue.QueueStore;
import eu.aston.user.UserException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QueueFlowBridge implements IQueueBridge, IFlowBridge {

    private final static Logger LOGGER = LoggerFactory.getLogger(QueueFlowBridge.class);
    public static final String QUEUE_PREFIX = "/queue/";

    private FlowCaseManager flowCaseManager;
    private QueueStore queueStore;
    private final ObjectMapper objectMapper;

    public QueueFlowBridge(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setFlowCaseManager(FlowCaseManager flowCaseManager) {
        this.flowCaseManager = flowCaseManager;
    }

    public void setQueueStore(QueueStore queueStore) {
        this.queueStore = queueStore;
    }

    @Override
    public boolean sendQueueEvent(String caseId, String taskId, String method, String path, Map<String, String> headers, byte[] body){
        if(queueStore!=null && path.startsWith(QUEUE_PREFIX) && path.length()> QUEUE_PREFIX.length()){
            LOGGER.debug("sendQueueEvent {}/{} {}", caseId, taskId, path);
            if(headers==null) headers = new HashMap<>();
            QueueEvent event = new QueueEvent();
            event.setId(taskId);
            event.setPath(path);
            event.setMethod(method);
            event.setHeaders(headers);
            headers.put(HeaderConverter.H_CASE_ID, caseId);
            headers.put(HeaderConverter.H_ID, taskId);
            headers.put(HeaderConverter.H_METHOD, event.getMethod());
            headers.put(HeaderConverter.H_URI, path);
            if(!headers.containsKey(HttpHeaders.CONTENT_TYPE)) headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

            event.setBody(body);
            queueStore.addEvent(event, -1);
            return true;
        }
        return false;
    }


    @Override
    public void queueEventSent(String id){
        if(flowCaseManager!=null) {
            flowCaseManager.queueEventSent(id);
        }
    }

    @Override
    public boolean eventResponse(QueueEvent event, int status, Map<String, String> headers, byte[] data){
        if(flowCaseManager!=null && event.getHeaders().containsKey(HeaderConverter.H_CASE_ID)){
            try {
                if(status>=200 && status<300) {
                    Object root = objectMapper.readValue(data, Object.class);
                    flowCaseManager.finishTask(event.getId(), status, root);
                } else {
                    flowCaseManager.finishTask(event.getId(), status, new String(data, StandardCharsets.UTF_8));
                }
                return true;
            }catch (Exception e){
                flowCaseManager.finishTask(event.getId(), 400, "parse json body error "+e.getMessage());
                throw new UserException("body not parse to json");
            }
        }
        return false;
    }

}
