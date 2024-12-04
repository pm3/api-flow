package eu.aston.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import eu.aston.blob.BlobStore;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowStepDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.model.FlowAsset;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.FlowCaseCreate;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.store.IFlowCaseStore;
import eu.aston.flow.store.IFlowTaskStore;
import eu.aston.flow.task.TaskExecutor;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.header.Callback;
import eu.aston.header.CallbackRunner;
import eu.aston.span.ISpanSender;
import eu.aston.user.UserException;
import eu.aston.utils.FlowThreadPool;
import eu.aston.utils.ID;
import eu.aston.utils.SuperTimer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FlowCaseManager {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowCaseManager.class);

    private final BlobStore blobStore;
    private final IFlowCaseStore caseStore;
    private final IFlowTaskStore taskStore;
    private final FlowDefStore flowDefStore;
    private final Map<String, IFlowExecutor> flowExecutorMap;
    private final WaitingFlowCaseManager waitingFlowCaseManager;
    private final ISpanSender spanSender;
    private final FlowThreadPool flowThreadPool;
    private final CallbackRunner callbackRunner;
    private final SuperTimer superTimer;
    private final TaskExecutor taskExecutor;

    public FlowCaseManager(BlobStore blobStore,
                           IFlowCaseStore caseStore,
                           IFlowTaskStore taskStore,
                           FlowDefStore flowDefStore,
                           IFlowExecutor[] executors,
                           WaitingFlowCaseManager waitingFlowCaseManager,
                           ISpanSender spanSender,
                           CallbackRunner callbackRunner,
                           SuperTimer superTimer, TaskExecutor taskExecutor) {
        this.blobStore = blobStore;
        this.caseStore = caseStore;
        this.taskStore = taskStore;
        this.flowExecutorMap = Arrays.stream(executors).collect(Collectors.toMap(IFlowExecutor::id, Function.identity()));
        this.flowDefStore = flowDefStore;
        this.waitingFlowCaseManager = waitingFlowCaseManager;
        this.spanSender = spanSender;
        this.callbackRunner = callbackRunner;
        this.superTimer = superTimer;
        this.taskExecutor = taskExecutor;
        this.flowThreadPool = new FlowThreadPool(4, this::nextTick);
    }

    public FlowDefStore getFlowDefStore() {
        return flowDefStore;
    }

    public ISpanSender getSpanSender() {
        return spanSender;
    }

    public IFlowCaseStore getCaseStore() {
        return caseStore;
    }

    public IFlowTaskStore getTaskStore() {
        return taskStore;
    }

    public BlobStore getBlobStore() {
        return blobStore;
    }

    public FlowThreadPool getFlowThreadPool() {
        return flowThreadPool;
    }

    public void createFlow(String id, FlowCaseCreate caseCreate) {
        Instant created = Instant.now();
        FlowDef flowDef = flowDefStore.flowDef(caseCreate.caseType())
                                      .orElseThrow(()->new UserException("invalid flowCaseType "+caseCreate.caseType()));
        List<FlowAsset> flowAssets = new ArrayList<>();
        if(caseCreate.assets()!=null){
            for(String assetId : caseCreate.assets()){
                try{
                    FlowAsset flowAsset = blobStore.loadAssetInfo(flowDef.getCode(), assetId);
                    if(flowAsset==null){
                        throw new UserException("invalid assetId "+assetId);
                    }
                    flowAsset.setUrl(null);
                    flowAssets.add(flowAsset);
                }catch (UserException e){
                    throw e;
                }catch (Exception e){
                    throw new UserException("load asset "+assetId);
                }
            }
        }

        FlowCaseEntity entity = new FlowCaseEntity();
        entity.setId(id);
        entity.setCaseType(caseCreate.caseType());
        entity.setExternalId(caseCreate.externalId());
        entity.setCallback(caseCreate.callback());
        entity.setParams(caseCreate.params());
        entity.setAssets(flowAssets);
        entity.setCreated(created);
        entity.setState(FlowCase.CREATED);
        caseStore.insert(entity);
        spanSender.createFlow(entity);
        flowThreadPool.addCase(id, id);
    }

    public FlowCase loadFlow(String id) {
        return caseStore.loadFlowCaseById(id);
    }

    public void finishTask(String taskId, int statusCode, Object response) {
        FlowTaskEntity taskEntity = taskStore.loadById(taskId)
                                             .orElseThrow(()->new UserException("undefined taskId "+taskId));
        if(taskEntity.getFinished()!=null){
            throw new UserException("task is finished "+ taskEntity.getId());
        }
        FlowCaseEntity flowCase = caseStore.loadById(taskEntity.getFlowCaseId())
                                           .orElseThrow(()->new UserException("undefined flowId "+taskEntity.getFlowCaseId()));
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));

        finishTask0(flowDef, flowCase, taskEntity, statusCode, response);
    }

    private void finishTask0(FlowDef flowDef, FlowCaseEntity flowCase, FlowTaskEntity task, int statusCode, Object response) {
        if(task.getFinished()!=null){
            throw new UserException("task is finished "+ task.getId());
        }

        LOGGER.info("finishTask {}/{} - {} - status {}", flowCase.getCaseType(), flowCase.getId(), task.getId(), statusCode);

        task.setFinished(Instant.now());
        task.setResponseCode(statusCode);

        String error = null;
        if(statusCode >=200 && statusCode <=202){
            taskStore.finishOk(task.getId(), statusCode, response);
            task.setResponse(response);
        } else {
            error = response !=null ? response.toString() : "";
            taskStore.finishError(task.getId(), statusCode, error);
            task.setError(error);
        }

        FlowWorkerDef workerDef = flowDefStore.cacheWorker(flowDef, task.getStep(), task.getWorker());
        spanSender.finishTask(flowCase, task, workerDef);
        flowThreadPool.addCase(task.getFlowCaseId(), task.getId());
    }

    public void taskTimeout(String taskId) {
        try{
            finishTask(taskId, 408, "timeout");
        }catch (UserException ignore){
        }
    }

    private void nextTick(String flowCaseId) {
        FlowCaseEntity flowCase = caseStore.loadById(flowCaseId)
                                      .orElseThrow(()->new UserException("invalid flowCaseId "+flowCaseId));
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));
        List<FlowTaskEntity> tasks = taskStore.selectTaskByCaseId(flowCase.getId());
        Map<String, FlowTaskEntity> taskMap = tasks.stream().collect(Collectors.toMap(FlowTaskEntity::getId, Function.identity()));
        LOGGER.info("nextTick {}/{} {}", flowCase.getCaseType(), flowCase.getId(), flowCase.getState());

        String aktStepCode = openTasks(flowDef, flowCase, tasks);
        if(aktStepCode!=null){
            IFlowExecutor executor = flowExecutorMap.get(flowDef.getExecutor());
            List<TaskHttpRequest> requests = executor.execTick(flowDef, flowCase, tasks, aktStepCode);
            for(TaskHttpRequest request : requests){
                FlowTaskEntity task = taskMap.get(request.taskId());
                if(request.error()!=null){
                    int statusCode = 400;
                    String message = request.error();
                    if(message.matches("^\\d+:.*")){
                        String[] parts = message.split(":", 2);
                        statusCode = Integer.parseInt(parts[0]);
                        message = parts[1];
                    }
                    finishTask(flowDef, flowCase, task, statusCode, message);
                } else {
                    try{
                        Runnable handleSend = ()->sentTask(task);
                        BiConsumer<Integer, Object> finishTask = (state, resp) -> finishTask(flowDef, flowCase, task, state, resp);
                        boolean debug = flowDef.getLabels()!=null && flowDef.getLabels().containsKey("debug");
                        taskExecutor.execute(request, task, handleSend, finishTask, true);
                    }catch (Exception e){
                        finishTask(flowDef, flowCase, task, 500, e.getMessage());
                    }
                }
            }
        }
    }

    private String openTasks(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {

        String aktState = FlowCaseManager.stepFromState(flowCase.getState());
        FlowStepDef aktStep = aktState!=null ? flowDef.getSteps().stream().filter(s-> Objects.equals(s.getCode(), aktState)).findFirst().orElse(null) : null;

        if(aktStep!=null) {
            int maxIndex = 1;
            boolean notStarted = false;
            boolean notFinished = false;
            Map<String, String> existMap = new HashMap<>();
            FlowTaskEntity iterator = null;
            String step = aktStep.getCode();
            for(FlowTaskEntity t : tasks){
                if(Objects.equals(step, t.getStep())){
                    if(t.getFinished() == null) notFinished=true;
                    existMap.put(t.getWorker()+":"+t.getStepIndex(), t.getId());
                    if(t.getWorker().equals(FlowTask.STEP_ITERATOR)){
                        iterator = t;
                    }
                }
            }
            if(aktStep.getItemsExpr()!=null && iterator==null){
                //vytvaram iterator
                FlowTaskEntity taskEntity = new FlowTaskEntity(ID.newId(), flowCase.getId(), aktStep.getCode(), FlowTask.STEP_ITERATOR, -1);
                taskEntity.setTimeout(flowDefStore.getDefaultTimeout());
                tasks.add(taskEntity);
                return aktStep.getCode();
            }
            if(iterator!=null){
                if(iterator.getFinished()==null){
                    //stale cakam na iterator
                    return null;
                }
                if(iterator.getResponse() instanceof List<?> items && !items.isEmpty()){
                    //iterator v poriadku
                    maxIndex = items.size();
                } else {
                    //chyba v iteratore, idem na dalsi krok
                    maxIndex = -1;
                    notFinished = false;
                }
            }
            for(int i=0; i<maxIndex; i++){
                for(FlowWorkerDef worker : aktStep.getWorkers()){
                    if(!existMap.containsKey(worker.getCode()+":"+i)){
                        tasks.add(createTask(flowCase.getId(), aktStep, worker, i));
                        notStarted = true;
                    }
                }
            }
            if (notStarted) {
                //mam rozpracovany step, pokracujem v spracovani
                return aktStep.getCode();
            }
            if(notFinished) {
                //este nie su vsetky hotovo, cakam na dalsi tick
                return null;
            }
        }
        //nemam rozpracovane tasky, idem na dalsi step
        FlowStepDef next = nextStep(flowCase, flowDef);
        if(next!=null){
            LOGGER.info("next step {} {}", flowCase.getId(), next.getCode());
            //spustam novy step
            flowCase.setState(FlowCase.STEP_STATE_PREF+next.getCode());
            caseStore.updateFlowState(flowCase.getId(), flowCase.getState());
            return openTasks(flowDef, flowCase, tasks);
        }
        //uz nemam next, koncim
        finishFlow(flowCase, flowDef, tasks);
        return null;
    }

    private FlowTaskEntity createTask(String caseId, FlowStepDef step, FlowWorkerDef worker, int stepIndex){
        FlowTaskEntity taskEntity = new FlowTaskEntity(ID.newId(), caseId, step.getCode(), worker.getCode(), stepIndex);
        taskEntity.setTimeout(worker.getTimeout()!=null ? worker.getTimeout() : flowDefStore.getDefaultTimeout());
        return taskEntity;
    }

    private FlowStepDef nextStep(FlowCaseEntity flowCase, FlowDef flowDef) {
        if(flowCase.getState()==null || Objects.equals(flowCase.getState(),FlowCase.CREATED)){
            return flowDef.getSteps().getFirst();
        }
        if(flowCase.getState().startsWith(FlowCase.STEP_STATE_PREF)){
            String stepCode = flowCase.getState().substring(FlowCase.STEP_STATE_PREF.length());
            List<FlowStepDef> steps = flowDef.getSteps();
            for(int i=1; i<steps.size(); i++){
                if(stepCode.equals(steps.get(i-1).getCode())){
                    return steps.get(i);
                }
            }
        }
        return null;
    }

    public void sentTask(FlowTaskEntity task) {
        if(task.getCreated()==null){
            task.setCreated(Instant.now());
            taskStore.insert(task);
            if(task.getTimeout()!=null){
                superTimer.schedule(task.getTimeout()*1000L, task.getId(), this::taskTimeout);
            }
        }
    }

    public void finishTask(FlowDef flowDef, FlowCaseEntity flowCase, FlowTaskEntity task, int statusCode, Object response) {
        if(task.getCreated()==null){
            task.setCreated(Instant.now());
            taskStore.insert(task);
        }
        finishTask0(flowDef, flowCase, task, statusCode, response);
    }

    private void finishFlow(FlowCaseEntity flowCaseEntity, FlowDef flowDef, List<FlowTaskEntity> tasks) {

        FlowTaskEntity finishTask = tasks.stream().filter(t->t.getWorker().equals(FlowTask.FLOW_RESPONSE)).findFirst().orElse(null);
        if(finishTask!=null){
            flowCaseEntity.setResponse(finishTask.getResponse());
            //tasks.remove(finishTask);
        }
        flowCaseEntity.setFinished(Instant.now());
        flowCaseEntity.setState(FlowCase.FINISHED);
        if(finishTask!=null && finishTask.getError()!=null){
            flowCaseEntity.setState(FlowCase.ERROR);
        }
        caseStore.finishFlow(flowCaseEntity.getId(), flowCaseEntity.getState(), flowCaseEntity.getResponse());
        LOGGER.info("finish flow {}/{} {}", flowCaseEntity.getCaseType(), flowCaseEntity.getId(), flowCaseEntity.getState());
        spanSender.finishFlow(flowCaseEntity, flowDef, null);

        //save to blob and clean db
        FlowCase flowCase = caseStore.loadFlowCaseById(flowCaseEntity.getId());
        flowCase.setTasks(taskStore.selectFlowTaskByCaseId(flowCaseEntity.getId()));
        Callback callback = flowCase.getCallback();
        flowCase.setCallback(null);
        try{
            blobStore.saveFinalCase(flowCaseEntity.getCaseType(), flowCase.getId(), flowCase);
            taskStore.deleteTasksByCaseId(flowCaseEntity.getId());
        }catch (Exception e){
            LOGGER.warn("saveFinalCase {}", e.getMessage(), e);
        }
        waitingFlowCaseManager.finished(flowCase);
        if(callback!=null){
            Map<String, String> headers = Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            callbackRunner.callbackAsync(flowCase.getId(), callback, headers, flowCase);
        }
    }

    private FlowAsset assetLink(String flowType, FlowAsset flowAsset) {
        try{
            return new FlowAsset(flowAsset.getId(), flowAsset.getExtName(), blobStore.createAssetUrl(flowType, flowAsset.getId()));
        }catch (Exception e){
            throw new UserException("create flow asset link "+flowAsset.getId()+" error "+e.getMessage());
        }
    }

    public void queueEventSent(String taskId) {
        taskStore.queueSent(taskId);
    }

    public static String stepFromState(String state){
        return state.startsWith(FlowCase.STEP_STATE_PREF) ? state.substring(FlowCase.STEP_STATE_PREF.length()) : null;
    }

    public void reprocess(String newCaseId, FlowCase flowCase2, List<FlowTask> tasks2) {
        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(newCaseId);
        flowCase.setCaseType(flowCase2.getCaseType());
        flowCase.setExternalId(flowCase2.getExternalId());
        flowCase.setCallback(flowCase2.getCallback());
        flowCase.setParams(flowCase2.getParams());
        flowCase.setAssets(flowCase2.getAssets());
        flowCase.setCreated(Instant.now());
        flowCase.setState(FlowCase.CREATED);
        caseStore.insert(flowCase);
        for(FlowTask task2 : tasks2) {
            FlowTaskEntity task = new FlowTaskEntity();
            task.setId(ID.newId());
            task.setFlowCaseId(newCaseId);
            task.setStep(task2.getStep());
            task.setWorker(task2.getWorker());
            task.setResponseCode(task2.getResponseCode());
            task.setResponse(task2.getResponse());
            task.setError(task2.getError());
            task.setCreated(flowCase.getCreated());
            task.setFinished(flowCase.getCreated());
            taskStore.insert(task);
        }
        flowThreadPool.addCase(newCaseId,newCaseId);
    }
}
