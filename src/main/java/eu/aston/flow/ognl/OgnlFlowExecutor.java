package eu.aston.flow.ognl;

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
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.header.CallbackRunner;
import eu.aston.user.UserException;
import jakarta.inject.Singleton;
import ognl.OgnlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OgnlFlowExecutor implements IFlowExecutor {

    private final static Logger LOGGER = LoggerFactory.getLogger(OgnlFlowExecutor.class);

    public static final String ID = "yaml";
    private final FlowDefStore flowDefStore;
    private final ObjectMapper objectMapper;

    public OgnlFlowExecutor(FlowDefStore flowDefStore, ObjectMapper objectMapper) {
        this.flowDefStore = flowDefStore;
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
    public List<TaskHttpRequest> execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks, String stepCode) {
        List<TaskHttpRequest> requests = new ArrayList<>();

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

        openedTasks.forEach((stepIndex, openTasks)->execTickStep(stepCode, stepIndex, flowDef, flowCase, openTasks, steps, requests));
        return requests;
    }

    private void execTickStep(String stepCode, int stepIndex, FlowDef flowDef, FlowCaseEntity flowCase,
                              List<FlowTaskEntity> openTasks, Map<String, FlowScript.LazyMap> steps, List<TaskHttpRequest> requests){

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
            TaskHttpRequest request = execTask(task, flowDef, flowCase, flowScript);
            if(request!=null) {
                requests.add(request);
            }
        }
    }

    private TaskHttpRequest execTask(FlowTaskEntity task, FlowDef flowDef, FlowCaseEntity flowCase, FlowScript flowScript) {

        FlowWorkerDef workerDef = flowDefStore.cacheWorker(flowDef, task.getStep(), task.getWorker());
        if(workerDef.getWhere()!=null){
            try{
                if(!flowScript.execWhere(workerDef.getWhere())){
                    return TaskHttpRequest.of(task.getId(), "406:where=false");
                }
            }catch (WaitingException e){
                return null;
            }catch (TaskResponseException e){
                return TaskHttpRequest.of(task.getId(), e.getMessage());
            }catch (OgnlException e){
                LOGGER.warn("ignore task {} where {}, exec error {}", task, workerDef.getWhere(), e.getMessage());
                return TaskHttpRequest.of(task.getId(),"execute where error "+e.getMessage());
            }
        }

        String error = null;
        try{
            return sendTask(flowScript, workerDef, flowCase, flowDef, task);
        }catch (WaitingException e) {
            return null;
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
            return TaskHttpRequest.of(task.getId(), error);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private TaskHttpRequest sendTask(FlowScript script, FlowWorkerDef workerDef, FlowCaseEntity flowCase, FlowDef flowDef, FlowTaskEntity task) throws Exception {

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

        String data = null;
        if(params!=null){
            try{
                data = objectMapper.writeValueAsString(params);
            }catch (JsonMappingException e){
                if(e.getCause() instanceof WaitingException e2) throw e2;
                if(e.getCause() instanceof TaskResponseException e2) throw e2;
                throw e;
            }
        }
        return new TaskHttpRequest(task.getId(), workerDef.getMethod(), path, headers, data, workerDef.isBlocked(), null);
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
