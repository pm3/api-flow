package eu.aston.flow.ognl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.IFlowDef;
import eu.aston.flow.def.CronJob;
import eu.aston.flow.def.JwtIssuerDef;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.user.UserException;
import eu.aston.utils.ID;
import ognl.OgnlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OgnlFlow implements IFlowDef {

    private final static Logger LOGGER = LoggerFactory.getLogger(OgnlFlow.class);

    private final String code;
    private final OgnlFlowData flowData;
    private final Map<String, WorkerDef> workerMap;
    private final List<String> steps;
    private final Set<String> iterableSteps;
    private final ObjectMapper objectMapper;
    private final int defaultTimeout;

    public OgnlFlow(String code, OgnlFlowData flowData, ObjectMapper objectMapper, int defaultTimeout) {
        this.code = code;
        this.flowData = flowData;
        this.objectMapper = objectMapper;
        this.defaultTimeout = defaultTimeout;

        if(flowData.response()!=null){
            WorkerDef w1 = new WorkerDef();
            w1.setName(FlowTask.FLOW_RESPONSE);
            w1.setPath("echo");
            w1.setParams(flowData.response());
            flowData.workers().add(w1);
        }
        LOGGER.debug("tasks {}", flowData.workers());

        this.workerMap = flowData.workers().stream().collect(Collectors.toMap(WorkerDef::getName, Function.identity()));
        this.steps = flowSteps(flowData.workers());
        LOGGER.debug("steps {}", steps);
        this.iterableSteps = steps.stream()
                                  .filter(s->s.endsWith("/_iterator"))
                                  .map(OgnlFlow::step)
                                  .collect(Collectors.toSet());
        LOGGER.debug("iterableSteps {}", iterableSteps);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public Map<String, String> getLabels() {
        return flowData.labels();
    }

    @Override
    public List<String> getAuthApiKeys() {
        return flowData.authApiKeys();
    }

    @Override
    public List<JwtIssuerDef> getAuthJwtIssuers() {
        return flowData.authJwtIssuers();
    }

    @Override
    public List<CronJob> getCronJobs() {
        return flowData.cronJobs();
    }

    @Override
    public List<TaskHttpRequest> execTick(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {
        List<TaskHttpRequest> requests = new ArrayList<>();
        String aktStep = openTasks(flowCase, tasks);
        if(aktStep==null) {
            return List.of();
        }
        if (aktStep.equals(FlowCase.FINISHED)){
            return List.of(TaskHttpRequest.of(FlowCase.FINISHED, FlowCase.FINISHED, 0, FlowCase.FINISHED));
        }

        Map<String, Object> workerContext = createWorkerContext(tasks);

        Map<Integer, List<FlowTaskEntity>> openedTasks = tasks.stream()
                                                              .filter(t->t.getFinished()==null && t.getCreated()==null)
                                                              .collect(Collectors.groupingBy(FlowTaskEntity::getStepIndex));

        for (Map.Entry<Integer, List<FlowTaskEntity>> entry : openedTasks.entrySet()) {
            execTickStep(aktStep, entry.getKey(), flowCase, entry.getValue(), workerContext, requests);
        }
        return requests;
    }

    @SuppressWarnings("rawtypes")
    private String openTasks(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {

        String aktState = flowCase.getState();
        if(aktState.equals(FlowCase.CREATED)){
            aktState = steps.getFirst();
        }
        int maxIndex = 1;
        boolean notStarted = false;
        boolean notFinished = false;
        Map<String, String> existMap = new HashMap<>();
        for(FlowTaskEntity t : tasks) {
            if (t.getWorker().startsWith(aktState)) {
                if (t.getFinished() == null)
                    notFinished = true;
                existMap.put(t.getWorker() + ":" + t.getStepIndex(), t.getId());
            }
            if(t.getWorker().equals(aktState+"/_iterator") && t.getResponse() instanceof List list){
                maxIndex = list.size();
            }
        }
        for(int i=0; i<maxIndex; i++){
            for(WorkerDef worker : flowData.workers()){
                if(worker.getName().startsWith(aktState) && !existMap.containsKey(worker.getName()+":"+i)){
                    tasks.add(createTask(flowCase.getId(), worker, i));
                    notStarted = true;
                }
            }
        }
        if (notStarted) {
            //mam rozpracovany step, pokracujem v spracovani
            return aktState;
        }
        if(notFinished) {
            //este nie su vsetky hotovo, cakam na dalsi tick
            return null;
        }
        //nemam rozpracovane tasky, idem na dalsi step
        String next = nextStep(aktState);
        if(next!=null){
            LOGGER.info("next step {} {}", flowCase.getId(), next);
            flowCase.setState(next);
            return openTasks(flowCase, tasks);
        }
        return FlowCase.FINISHED;
    }

    private FlowTaskEntity createTask(String caseId, WorkerDef worker, int stepIndex){
        FlowTaskEntity taskEntity = new FlowTaskEntity(ID.newId(), caseId, worker.getName(), stepIndex);
        taskEntity.setTimeout(worker.getTimeout()!=null ? worker.getTimeout() : defaultTimeout);
        return taskEntity;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createWorkerContext(List<FlowTaskEntity> tasks) {
        Map<String, Object> workerMap = new HashMap<>();
        for(FlowTaskEntity t : tasks){
            String[] items = t.getWorker().split("/");
            String stepName = items[0];
            String workerName = items.length>1 ? items[1] : items[0];
            if(iterableSteps.contains(items[0])){
                int index = t.getStepIndex();
                List<StepMap> list = (List<StepMap>)workerMap.computeIfAbsent(stepName, (k)->new ArrayList<>());
                while(list.size()<index+1) list.add(new StepMap());
                taskToMap(list.get(index), workerName, t);
            } else {
                Map<String, Object> stepMap = items.length == 1 ? workerMap : (Map<String, Object>) workerMap.computeIfAbsent(items[0], (k) -> new StepMap());
                taskToMap(stepMap, workerName, t);
            }
        }
        return workerMap;
    }


    @SuppressWarnings("rawtypes")
    private void execTickStep(String stepCode, int stepIndex, FlowCaseEntity flowCase,
                              List<FlowTaskEntity> openTasks, Map<String, Object> workerContext, List<TaskHttpRequest> requests){

        LOGGER.debug("opened tasks {}", openTasks.stream().map(FlowTaskEntity::getWorker).toList());

        Map<String, Object> root = new HashMap<>();
        root.put("case", flowCase);
        root.put("worker", workerContext);

        if(workerContext.get(stepCode) instanceof StepMap stepMap){
            root.putAll(stepMap);
        }

        if(stepIndex>=0 && workerContext.get(stepCode) instanceof List list){
            if(list.size()>stepIndex && list.get(stepIndex) instanceof StepMap stepMap){
                root.putAll(stepMap);
            }
        }

        LOGGER.debug("root {}", root);
        FlowScript flowScript = new FlowScript(root);
        for(FlowTaskEntity task : openTasks){
            TaskHttpRequest request = execTask(task, stepCode, flowCase, flowScript);
            if(request!=null) {
                requests.add(request);
            }
        }
    }

    private TaskHttpRequest execTask(FlowTaskEntity task, String step, FlowCaseEntity flowCase, FlowScript flowScript) {

        WorkerDef workerDef = workerMap.get(task.getWorker());
        if(workerDef.getWhere()!=null){
            try{
                if(!flowScript.execWhere(workerDef.getWhere())){
                    return TaskHttpRequest.of(task.getWorker(), step, task.getStepIndex(), "406:where=false");
                }
            }catch (WaitingException e){
                return null;
            }catch (TaskResponseException e){
                return TaskHttpRequest.of(task.getWorker(), step, task.getStepIndex(), e.getMessage());
            }catch (OgnlException e){
                LOGGER.warn("ignore task {} where {}, exec error {}", task, workerDef.getWhere(), e.getMessage());
                return TaskHttpRequest.of(task.getWorker(), step, task.getStepIndex(),"execute where error "+e.getMessage());
            }
        }

        String error = null;
        try{
            return createTaskRequest(flowScript, workerDef, flowCase, task, step);
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
            return TaskHttpRequest.of(task.getWorker(), step, task.getStepIndex(), error);
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    private TaskHttpRequest createTaskRequest(FlowScript script, WorkerDef workerDef, FlowCaseEntity flowCase, FlowTaskEntity task, String step) throws Exception {

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
        return new TaskHttpRequest(task.getWorker(), step, task.getStepIndex(), workerDef.getMethod(), path, headers, data, workerDef.isBlocked(), null);
    }

    private void taskToMap(Map<String, Object> stepMap, String name, FlowTaskEntity t) {
        Object resp = t.getResponse();
        if(t.getFinished()==null){
            resp = new WaitingException(t.getWorker());
        } else if (t.getError()!=null){
            resp = new TaskResponseException("error "+t.getWorker()+" "+t.getResponseCode());
        }
        stepMap.put(name, resp);
    }

    private List<String> flowSteps(List<WorkerDef> workers) {
        List<String> steps = new ArrayList<>();
        for(WorkerDef worker : workers){
            String step = step(worker.getName());
            if(worker.getName().endsWith("/_iterator")){
                if(steps.contains(step)){
                    throw new IllegalArgumentException("iterator musi byt definovany ako prvy v stepe");
                }
                steps.add(worker.getName());
            } else {
                if(!steps.contains(step)) steps.add(step);
            }
        }
        return steps;
    }

    private String nextStep(String akt){
        for(int i=0; i<steps.size()-1; i++) {
            if(steps.get(i).equals(akt)) return steps.get(i+1);
        }
        return null;
    }

    public static String step(String name){
        int pos = name.indexOf('/');
        return pos>0 ? name.substring(0, pos) : name;
    }

    public static class StepMap extends HashMap<String, Object>{
    }
}
