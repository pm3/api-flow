package eu.aston.flow.ognl;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.aston.AppConfig;
import eu.aston.flow.FlowDefStore;
import eu.aston.flow.IFlowExecutor;
import eu.aston.flow.QueueFlowBridge;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowStepDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.header.CallbackRunner;
import eu.aston.header.HeaderConverter;
import eu.aston.user.UserException;
import eu.aston.utils.Hash;
import jakarta.inject.Singleton;
import ognl.OgnlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class YamlOgnlFlowExecutor implements IFlowExecutor {

    private final static Logger LOGGER = LoggerFactory.getLogger(YamlOgnlFlowExecutor.class);

    public static final String ID = "yaml";
    private final FlowDefStore flowDefStore;
    private final QueueFlowBridge flowBridge;
    private final AppConfig appConfig;
    private final byte[] taskApiKeySecret;
    private final ObjectMapper objectMapper;
    private final CallbackRunner callbackRunner;

    public YamlOgnlFlowExecutor(FlowDefStore flowDefStore, QueueFlowBridge flowBridge, AppConfig appConfig,
                                ObjectMapper objectMapper, CallbackRunner callbackRunner) {
        this.flowDefStore = flowDefStore;
        this.flowBridge = flowBridge;
        this.appConfig = appConfig;
        this.taskApiKeySecret = appConfig.getTaskApiKeySecret().getBytes(StandardCharsets.UTF_8);
        this.callbackRunner = callbackRunner;

        this.objectMapper = objectMapper.copy();
        SimpleModule module = new SimpleModule();
        module.addSerializer(TaskResponseException.class, new RuntimeExceptionSerializer());
        this.objectMapper.registerModule(module);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks,
                         String stepCode, IFlowBack flowBack) {

        List<String> iterableSteps = flowDef.getSteps().stream()
                                            .filter(s->s.getItemsExpr()!=null)
                                            .map(FlowStepDef::getCode)
                                            .toList();

        Map<String, FlowScript.LazyMap> steps = new HashMap<>();
        for(FlowTaskEntity t : tasks){
            FlowScript.LazyMap stepMap = steps.computeIfAbsent(t.getStep(), (k)->new FlowScript.LazyMap());
            if(t.getWorker().equals(FlowTask.STEP_ITERATOR)){
                stepMap = steps.computeIfAbsent(t.getStep(), (k)->new FlowScript.LazyMap());
                taskToMap(stepMap, t, -1);
            } else if(iterableSteps.contains(t.getStep())){
                taskToMap(stepMap, t, t.getStepIndex());
            } else {
                taskToMap(stepMap, t, -1);
            }
        }

        Map<Integer, List<FlowTaskEntity>> openedTasks = tasks.stream()
                .filter(t->t.getFinished()==null && t.getCreated()==null)
                .collect(Collectors.groupingBy(FlowTaskEntity::getStepIndex));

        openedTasks.forEach((stepIndex, openTasks)->execTickStep(stepCode, stepIndex, flowDef, flowCase, openTasks, steps, flowBack));
    }

    private void execTickStep(String stepCode, int stepIndex, FlowDef flowDef, FlowCaseEntity flowCase,
                              List<FlowTaskEntity> openTasks, Map<String, FlowScript.LazyMap> steps, IFlowBack flowBack){

        LOGGER.debug("next tick {}",flowCase.getId());
        LOGGER.debug("opened tasks {}", openTasks.stream().map(FlowTaskEntity::getWorker).toList());

        Map<String, Object> root = new FlowScript.LazyMap();
        root.put("case", flowCase);
        root.put("step", steps);
        Map<String, Object> aktStepMap = steps.get(stepCode);
        if(aktStepMap!=null){
            aktStepMap.forEach((k,v)->{
                if(v.equals(FlowTask.STEP_ITERATOR)){
                    root.put(k,v);
                } else if(v instanceof List<?> l){
                    if(l.size()>=stepIndex+1) root.put(k, l.get(stepIndex));
                } else {
                    root.put(k,v);
                }
            });
        }

        LOGGER.debug("root {}", root);
        FlowScript flowScript = new FlowScript(root);
        for(FlowTaskEntity task : openTasks){
            execTask(task, flowDef, flowCase, flowScript, flowBack);
        }
    }

    private void execTask(FlowTaskEntity task, FlowDef flowDef, FlowCaseEntity flowCase, FlowScript flowScript, IFlowBack flowBack) {

        FlowWorkerDef workerDef = flowDefStore.cacheWorker(flowDef, task.getStep(), task.getWorker());
        if(workerDef.getWhere()!=null){
            try{
                if(!flowScript.execWhere(workerDef.getWhere())){
                    flowBack.finishTask(task, 406, "where=false");
                    return;
                }
            }catch (WaitingException e){
                return;
            }catch (TaskResponseException e){
                flowBack.finishTask(task, 400, e.getMessage());
                return;
            }catch (OgnlException e){
                LOGGER.warn("ignore task {} where {}, exec exception {}", task, workerDef.getWhere(), e.getMessage());
                flowBack.finishTask(task, 400, "execute where exception "+e.getMessage());
                return;
            }
        }

        String error = null;
        try{
            sendTask(flowScript, workerDef, flowCase, flowDef, task, flowBack);
        }catch (WaitingException e) {
            return;
        }catch (TaskResponseException e) {
            error = e.getMessage();
        }catch (OgnlException e) {
            LOGGER.warn("ognl exception {}",e.getMessage());
            error = "parse params error "+e.getMessage();
        }catch (Exception e) {
            LOGGER.warn("run task exception {}",e.getMessage());
            error = "send to worker error "+e.getMessage();
        }
        if(error!=null){
            task.setError(error);
            flowBack.finishTask(task, 400, error);
        }
    }

    @SuppressWarnings("rawtypes")
    private void sendTask(FlowScript script, FlowWorkerDef workerDef, FlowCaseEntity flowCase, FlowDef flowDef, FlowTaskEntity task, IFlowBack flowBack) throws Exception {

        String path = workerDef.getPath();

        if(path==null && workerDef.getPathExpr()!=null) {
            Object o = script.execExpr(workerDef.getPathExpr());
            path = o!=null ? o.toString() : null;
        }
        if(path==null) throw new UserException("task has empty path");

        Map<String,String> headers = null;
        if(workerDef.getHeaders()!=null){
            headers = script.execMapS(workerDef.getHeaders());
        }

        Object params = null;
        if(workerDef.getParams()!=null){
            params = script.execMap(workerDef.getParams());
            if (params instanceof Map paramsMap && paramsMap.size()==1 && paramsMap.containsKey(".")){
                params = paramsMap.get(".");
            }
        }

        sendTaskHttp(task, flowDef, workerDef.getMethod(), path, headers, params, workerDef.isBlocked(), flowBack);
    }

    protected void sendTaskHttp(FlowTaskEntity task, FlowDef flowDef, String method, String path, Map<String, String> headers, Object params, boolean blocked, IFlowBack flowBack) throws Exception {
        byte[] data = null;
        if(params!=null){
            try{
                data = objectMapper.writeValueAsBytes(params);
            }catch (JsonMappingException e){
                if(e.getCause() instanceof WaitingException e2) throw e2;
                if(e.getCause() instanceof TaskResponseException e2) throw e2;
                throw e;
            }
        }

        flowBack.sentTask(task);

        if(path.equals(FlowTask.ECHO)){
            flowBack.finishTask(task, 200, params);
            return;
        }

        if(flowBridge.sendQueueEvent(task, method, path, headers, data, flowBack)){
            return;
        }
        URI uri = new URI(path);
        if(uri.getHost()==null){
            uri = new URI(appConfig.getAppHost()).resolve(path);
        }
        Map<String, String> headers2 = new HashMap<>();
        if(headers!=null) headers2.putAll(headers);
        headers2.put(HeaderConverter.H_CASE_ID, task.getFlowCaseId());
        headers2.put(HeaderConverter.H_ID, task.getId());
        headers2.put("X-B3-TraceId", task.getFlowCaseId());
        headers2.put("X-B3-SpanId", task.getId().substring(0,15)+"2");

        LOGGER.info("http task {} <= {}/{} - {}", path, task.getFlowCaseId(), task.getId(), task.getWorker());
        if(data!=null && flowDef.getLabels()!=null && flowDef.getLabels().containsKey("debug")){
            LOGGER.info("data {}", new String(data));
        } else if(data!=null && LOGGER.isDebugEnabled()){
            LOGGER.debug("data {}", new String(data));
        }
        flowBack.sentTask(task);
        if(blocked){
            callbackRunner.callAsync(method, uri, headers2, data, HttpResponse.BodyHandlers.ofByteArray())
                          .whenComplete((resp, e)-> completeCallBlocked(resp, e, task, flowBack));
        } else {
            String callbackPath = "/flow/response/"+ task.getId();
            headers2.put(HeaderConverter.H_CALLBACK_URL, new URI(appConfig.getAppHost()).resolve(callbackPath).toString());
            String apiKey = Hash.hmacSha1(task.getId().getBytes(StandardCharsets.UTF_8), taskApiKeySecret);
            headers2.put(HeaderConverter.H_CALLBACK_PREFIX+"x-api-key", apiKey);

            callbackRunner.callAsync(method, uri, headers2, data, HttpResponse.BodyHandlers.ofByteArray())
                          .whenComplete((resp, e)-> completeCall(resp, e, task, flowBack));
        }
    }

    private void completeCallBlocked(HttpResponse<byte[]> resp, Throwable e, FlowTaskEntity task, IFlowBack flowBack){
        if (resp!=null && resp.statusCode() >= 200 && resp.statusCode()<=202) {
            try {
                Object root = objectMapper.readValue(resp.body(), Object.class);
                flowBack.finishTask(task, resp.statusCode(), root);
            }catch (Exception e2) {
                flowBack.finishTask(task, 400, "response body invalid json "+e2.getMessage());
            }
        } else if (resp!=null) {
            int status = Math.max(resp.statusCode(), 400);
            flowBack.finishTask(task, resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8));
        }
        if(e!=null){
            flowBack.finishTask(task, 500, "httpClient "+e.getMessage());
        }
    }

    private void completeCall(HttpResponse<byte[]> resp, Throwable e, FlowTaskEntity task, IFlowBack flowBack){
        if (resp!=null && resp.statusCode()>299) {
            int status = Math.max(resp.statusCode(), 400);
            flowBack.finishTask(task, resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8));
        }
        if(e!=null){
            flowBack.finishTask(task, 500, "httpClient "+e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void taskToMap(FlowScript.LazyMap stepMap, FlowTaskEntity t, int index) {
        Object resp = t.getResponse();
        if(t.getFinished()==null){
            resp = new WaitingException(t.getWorker());
        } else if (t.getError()!=null){
            resp = new TaskResponseException("error "+t.getWorker()+" "+t.getResponseCode());
        }
        if(index<0){
            stepMap.put(t.getWorker(), resp);
        } else {
            List<Object> list = (List<Object>)stepMap.computeIfAbsent(t.getWorker(), (k)->new ArrayList<>());
            while(list.size()<index+1) list.add(null);
            list.set(index, resp);
        }
    }
}
