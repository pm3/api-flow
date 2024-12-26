package eu.aston.flow.task;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.AppConfig;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.header.CallbackRunner;
import eu.aston.header.HeaderConverter;
import eu.aston.utils.Hash;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
public class TaskExecutor {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(TaskExecutor.class);

    private final ObjectMapper objectMapper;
    private final CallbackRunner callbackRunner;
    private final AppConfig appConfig;
    private final byte[] taskApiKeySecret;

    public TaskExecutor(ObjectMapper objectMapper, CallbackRunner callbackRunner,
                        AppConfig appConfig) {
        this.objectMapper = objectMapper;
        this.callbackRunner = callbackRunner;
        this.appConfig = appConfig;
        this.taskApiKeySecret = appConfig.getTaskApiKeySecret().getBytes(StandardCharsets.UTF_8);
    }

    public void execute(TaskHttpRequest request, FlowTaskEntity task, Runnable handleSend, BiConsumer<Integer, Object> finishTask, boolean debug) throws Exception {
        if(request.path().equals(FlowTask.ECHO)){
            Object params = objectMapper.readValue(request.body(), Object.class);
            finishTask.accept(200, params);
            return;
        }
        URI uri = new URI(request.path());
        if(uri.getHost()==null && uri.getPath().startsWith("/queue/")){
            Map<String, String> headers2 = HeaderConverter.queueRequest(request);
            request = new TaskHttpRequest(request.taskId(), "POST", appConfig.getQueueHost()+uri.getPath(), headers2 , request.body(), false, null);
        } else if(uri.getHost()==null){
            uri = new URI(appConfig.getAppHost()).resolve(request.path());
        }
        Map<String, String> headers2 = new HashMap<>();
        if(request.headers()!=null) headers2.putAll(request.headers());
        headers2.put(HeaderConverter.H_CASE_ID, task.getFlowCaseId());
        headers2.put(HeaderConverter.H_ID, task.getId());
        headers2.put("X-B3-TraceId", task.getFlowCaseId());
        headers2.put("X-B3-SpanId", task.getId().substring(0,15)+"2");

        LOGGER.info("http task {} <= {}/{} - {}", request.path(), task.getFlowCaseId(), task.getId(), task.getWorker());
        if(request.body()!=null && debug){
            LOGGER.info("body {}", request.body());
        } else if(request.body()!=null && LOGGER.isDebugEnabled()){
            LOGGER.debug("body {}", request.body());
        }
        handleSend.run();
        if(request.blocked()){
            callbackRunner.callAsync(request.method(), uri, headers2, request.body())
                          .whenComplete((resp, e)-> completeCallBlocked(finishTask, resp, e, task));
        } else {
            String callbackPath = "/flow/response/"+ task.getId();
            headers2.put(HeaderConverter.H_CALLBACK_URL, new URI(appConfig.getAppHost()).resolve(callbackPath).toString());
            String apiKey = Hash.hmacSha1(task.getId().getBytes(StandardCharsets.UTF_8), taskApiKeySecret);
            headers2.put(HeaderConverter.H_CALLBACK_PREFIX+"x-api-key", apiKey);

            callbackRunner.callAsync(request.method(), uri, headers2, request.body())
                          .whenComplete((resp, e)-> completeCall(finishTask, resp, e, task));
        }
    }

    private void completeCallBlocked(BiConsumer<Integer, Object> finishTask, HttpResponse<byte[]> resp, Throwable e, FlowTaskEntity task){
        if (resp!=null && resp.statusCode() >= 200 && resp.statusCode()<=202) {
            try {
                Object root = objectMapper.readValue(resp.body(), Object.class);
                finishTask.accept(resp.statusCode(), root);
            }catch (Exception e2) {
                finishTask.accept(400, "response body invalid json "+e2.getMessage());
            }
        } else if (resp!=null) {
            int status = Math.max(resp.statusCode(), 400);
            finishTask.accept(status, new String(resp.body(), StandardCharsets.UTF_8));
        }
        if(e!=null){
            finishTask.accept(500, "httpClient "+e.getMessage());
        }
    }

    private void completeCall(BiConsumer<Integer, Object> finishTask, HttpResponse<byte[]> resp, Throwable e, FlowTaskEntity task){
        if (resp!=null && resp.statusCode()>299) {
            int status = Math.max(resp.statusCode(), 400);
            finishTask.accept(resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8));
        }
        if(e!=null){
            finishTask.accept(500, "httpClient "+e.getMessage());
        }
    }

}
