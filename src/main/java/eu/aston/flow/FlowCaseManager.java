package eu.aston.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import eu.aston.blob.BlobStore;
import eu.aston.flow.model.FlowAsset;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.FlowCaseCreate;
import eu.aston.flow.model.FlowRequest;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowRequestEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.store.IFlowCaseStore;
import eu.aston.flow.store.IFlowRequestStore;
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
    private final IFlowRequestStore requestStore;
    private final FlowDefStore flowDefStore;
    private final WaitingFlowCaseManager waitingFlowCaseManager;
    private final ISpanSender spanSender;
    private final FlowThreadPool flowThreadPool;
    private final CallbackRunner callbackRunner;
    private final SuperTimer superTimer;
    private final TaskExecutor taskExecutor;

    public FlowCaseManager(BlobStore blobStore,
                           IFlowCaseStore caseStore,
                           IFlowTaskStore taskStore, IFlowRequestStore requestStore,
                           FlowDefStore flowDefStore,
                           WaitingFlowCaseManager waitingFlowCaseManager,
                           ISpanSender spanSender,
                           CallbackRunner callbackRunner,
                           SuperTimer superTimer, TaskExecutor taskExecutor) {
        this.blobStore = blobStore;
        this.caseStore = caseStore;
        this.taskStore = taskStore;
        this.requestStore = requestStore;
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
        IFlowDef flowDef = flowDefStore.flowDef(caseCreate.caseType())
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
        IFlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                       .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));

        finishTask0(flowDef, flowCase, taskEntity, statusCode, response);
    }

    private void finishTask0(IFlowDef flowDef, FlowCaseEntity flowCase, FlowTaskEntity task, int statusCode, Object response) {
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

        FlowRequestEntity request = requestStore.loadBaseById(task.getId());
        spanSender.finishTask(flowCase, task, request);
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
        IFlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                       .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));
        List<FlowTaskEntity> tasks = taskStore.selectTaskByCaseId(flowCase.getId());
        LOGGER.info("nextTick {}/{} {}", flowCase.getCaseType(), flowCase.getId(), flowCase.getState());

        List<TaskHttpRequest> requests = flowDef.execTick(flowCase, tasks);
        if(requests.size()==1 && Objects.equals(requests.getFirst().step(), FlowCase.FINISHED)){
            finishFlow(flowCase, flowDef, tasks);
            return;
        }
        if(!requests.isEmpty() && !requests.getLast().step().equals(flowCase.getState())){
            caseStore.updateFlowState(flowCase.getId(), requests.getLast().step());
        }
        for(TaskHttpRequest request : requests){
            FlowTaskEntity task = new FlowTaskEntity(ID.newId(), flowCase.getId(), request.worker(), request.stepIndex());
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
                    Runnable handleSend = ()->sentTask(task, request);
                    BiConsumer<Integer, Object> finishTask = (state, resp) -> finishTask(flowDef, flowCase, task, state, resp);
                    boolean debug = flowDef.getLabels()!=null && flowDef.getLabels().containsKey("debug");
                    taskExecutor.execute(request, task, handleSend, finishTask, true);
                }catch (Exception e){
                    finishTask(flowDef, flowCase, task, 500, e.getMessage());
                }
            }
        }
    }

    public void sentTask(FlowTaskEntity task, TaskHttpRequest request) {
        if(task.getCreated()==null){
            task.setCreated(Instant.now());
            taskStore.insert(task);
            if(task.getTimeout()!=null){
                superTimer.schedule(task.getTimeout()*1000L, task.getId(), this::taskTimeout);
            }
        }
        requestStore.insert(new FlowRequestEntity(task.getId(), task.getFlowCaseId(), request.method(), request.path(), request.body()));
    }

    public void finishTask(IFlowDef flowDef, FlowCaseEntity flowCase, FlowTaskEntity task, int statusCode, Object response) {
        if(task.getCreated()==null){
            task.setCreated(Instant.now());
            taskStore.insert(task);
        }
        finishTask0(flowDef, flowCase, task, statusCode, response);
    }

    private void finishFlow(FlowCaseEntity flowCaseEntity, IFlowDef flowDef, List<FlowTaskEntity> tasks) {

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
        Map<String, FlowRequest> requests = requestStore.selectByCaseId(flowCaseEntity.getId())
                .stream().collect(Collectors.toMap(FlowRequestEntity::getId, r->new FlowRequest(r.getMethod(), r.getPath(), r.getBody())));
        for (FlowTask task : flowCase.getTasks()) {
            task.setRequest(requests.get(task.getId()));
        }
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
