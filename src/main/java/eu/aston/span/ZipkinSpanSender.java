package eu.aston.span;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.utils.ID;
import eu.aston.utils.SuperTimer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Requires(property = "zipkin.url")
public class ZipkinSpanSender implements ISpanSender {

    private final static Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanSender.class);

    private final String zipkinUrl;
    private final String zipkinAuth;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private List<ZipkinSpan> cache = Collections.synchronizedList(new ArrayList<>());
    private boolean disableCache = false;

    public ZipkinSpanSender(@Value("${zipkin.url}") String zipkinUrl,
                            @Value("${zipkin.baseAuth:}") String zipkinBaseAuth,
                            HttpClient httpClient,
                            ObjectMapper objectMapper,
                            SuperTimer superTimer) {
        this.zipkinUrl = zipkinUrl;
        this.zipkinAuth = StringUtils.isNotEmpty(zipkinBaseAuth) ? "Basic "+ Base64.getEncoder().encodeToString(zipkinBaseAuth.getBytes(
                StandardCharsets.UTF_8)) : null;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        Executor executor = Executors.newSingleThreadExecutor();
        superTimer.schedulePeriod(10_000L, ()->executor.execute(this::send));
    }

    public void setDisableCache(boolean disableCache) {
        this.disableCache = disableCache;
    }

    private void send() {
        if(!cache.isEmpty()){
            List<ZipkinSpan> cache0 = this.cache;
            this.cache = Collections.synchronizedList(new ArrayList<>());
            sendCache(cache0);
        }
    }

    private void sendCache(List<ZipkinSpan> cache0) {

        try{
            String data = objectMapper.writeValueAsString(cache0);
            if(zipkinUrl.equals("none")){
                for(ZipkinSpan s : cache0) LOGGER.info("span {}", objectMapper.writeValueAsString(s));
                return;
            }
            HttpRequest.Builder b = HttpRequest
                    .newBuilder()
                    .uri(new URI(zipkinUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            if(zipkinAuth!=null) b.header(HttpHeaders.AUTHORIZATION, zipkinAuth);
            HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode()!=202) throw new Exception("status code "+resp.statusCode()+" "+resp.body());
            LOGGER.debug("send {}", cache0.getFirst().getName());
        }catch (Exception e){
            LOGGER.error("send to zipkin {}", e.getMessage());
        }
    }

    private void cacheAdd(ZipkinSpan span){
        if(disableCache){
            sendCache(List.of(span));
        } else {
            cache.add(span);
        }
    }

    private String createId(){
        return ID.newId().substring(0, 16);
    }

    @Override
    public void createFlow(FlowCaseEntity flowCase) {
        ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(flowCase.getId());
        span.setName("flow-start");
        span.setId(flowCase.getId().substring(0,15)+"1");
        span.setTimestamp(flowCase.getCreated().toEpochMilli()*1000);
        span.setDuration(Duration.between(flowCase.getCreated(), Instant.now()).toMillis()*1000);
        if(span.getDuration()<3000) span.setDuration(3000);
        span.setLocalEndpoint(new ZipkinEndpoint("/flow/"+flowCase.getCaseType()));
        cacheAdd(span);
    }

    @Override
    public void finishFlow(FlowCaseEntity flowCase, FlowDef flowDef, String error) {
        ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(flowCase.getId());
        span.setName("flow");
        span.setId(flowCase.getId().substring(0,15)+"0");
        span.setTimestamp(flowCase.getCreated().toEpochMilli()*1000);
        span.setDuration(Duration.between(flowCase.getCreated(), flowCase.getFinished()).toMillis()*1000);
        span.setLocalEndpoint(new ZipkinEndpoint("/flow/"+flowCase.getCaseType()));
        span.setTags(new HashMap<>());
        if(error!=null) {
            span.getTags().put("error", error);
            span.setName(span.getName()+" error");
        }
        if(flowDef.getLabels()!=null && !flowDef.getLabels().isEmpty()){
            flowDef.getLabels().forEach((k,v)->span.getTags().put("flow."+k, v));
        }
        if(flowCase.getExternalId()!=null) {
            span.getTags().put("case.externalId", flowCase.getExternalId());
        }
        span.getTags().put("case.id", flowCase.getId());
        span.getTags().put("case.type", flowCase.getCaseType());
        cacheAdd(span);
    }

    @Override
    public void finishWaitingTask(FlowCaseEntity flowCase, FlowTaskEntity task, String error) {
        if(task.getStarted()==null) return;
        ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(flowCase.getId());
        span.setName("task-waiting");
        span.setId(task.getId().substring(0,15)+"1");
        span.setParentId(task.getId().substring(0,15)+"0");
        span.setTimestamp(task.getCreated().toEpochMilli()*1000);
        span.setDuration(Duration.between(task.getCreated(), task.getStarted()).toMillis()*1000);
        if(span.getDuration()<2000) return;
        span.setLocalEndpoint(new ZipkinEndpoint("/flow/"+flowCase.getCaseType()+"/"+task.getStep()+"/"+task.getWorker()));
        if(error!=null){
            span.setTags(new HashMap<>());
            span.getTags().put("error", error);
            span.setName(span.getName()+" error");
        }
        cacheAdd(span);
    }

    @Override
    public void finishRunningTask(FlowCaseEntity flowCase, FlowTaskEntity task, FlowWorkerDef workerDef, int statusCode, String error) {
        if(task.getStarted()==null) return;
        ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(flowCase.getId());
        span.setName("task-running");
        span.setId(task.getId().substring(0,15)+"2");
        span.setParentId(task.getId().substring(0,15)+"0");
        span.setTimestamp(task.getStarted().toEpochMilli()*1000);
        span.setDuration(Duration.between(task.getStarted(), task.getFinished()).toMillis()*1000);
        span.setLocalEndpoint(new ZipkinEndpoint("/flow/"+flowCase.getCaseType()+"/"+task.getStep()+"/"+task.getWorker()));
        span.setTags(new HashMap<>());
        if(error!=null){
            span.getTags().put("error", error);
            span.setName(span.getName()+" error");
        }
        span.getTags().put("http.method", workerDef.getMethod());
        span.getTags().put("http.path", workerDef.getPath()!=null? workerDef.getPath() : workerDef.getPathExpr());
        span.getTags().put("http.status_code", task.getError()!=null ? "400":"200");
        cacheAdd(span);
    }

    @Override
    public void finishTask(FlowCaseEntity flowCase, FlowTaskEntity task, FlowWorkerDef workerDef) {
        ZipkinSpan span = new ZipkinSpan();
        span.setTraceId(flowCase.getId());
        span.setName("task");
        span.setId(task.getId().substring(0,15)+"0");
        span.setTimestamp(task.getCreated().toEpochMilli()*1000);
        span.setDuration(Duration.between(task.getCreated(), task.getFinished()).toMillis()*1000);
        span.setLocalEndpoint(new ZipkinEndpoint("/flow/"+flowCase.getCaseType()+"/"+task.getStep()+"/"+task.getWorker()));
        span.setTags(new HashMap<>());
        if(workerDef.getLabels()!=null && !workerDef.getLabels().isEmpty()){
            span.getTags().putAll(workerDef.getLabels());
        }
        span.getTags().put("step", task.getStep());
        if(task.getStepIndex()>=0) span.getTags().put("StepIndex", String.valueOf(task.getStepIndex()));
        span.getTags().put("worker", workerDef.getCode());
        if(workerDef.getLabels()!=null){
            workerDef.getLabels().forEach((k,v)->span.getTags().put("worker."+k, v));
        }
        cacheAdd(span);
    }
}
