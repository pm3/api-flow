package eu.aston.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import eu.aston.header.Callback;
import eu.aston.header.CallbackRunner;
import eu.aston.span.ISpanSender;
import eu.aston.user.UserException;
import eu.aston.utils.FlowThreadPool;
import eu.aston.utils.ID;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public FlowCaseManager(BlobStore blobStore,
                           IFlowCaseStore caseStore,
                           IFlowTaskStore taskStore,
                           FlowDefStore flowDefStore,
                           IFlowExecutor[] executors,
                           WaitingFlowCaseManager waitingFlowCaseManager,
                           ISpanSender spanSender,
                           CallbackRunner callbackRunner) {
        this.blobStore = blobStore;
        this.caseStore = caseStore;
        this.taskStore = taskStore;
        this.flowExecutorMap = Arrays.stream(executors).collect(Collectors.toMap(IFlowExecutor::id, Function.identity()));
        this.flowDefStore = flowDefStore;
        this.waitingFlowCaseManager = waitingFlowCaseManager;
        this.spanSender = spanSender;
        this.callbackRunner = callbackRunner;
        this.flowThreadPool = new FlowThreadPool(4, this::nextTick);
    }

    public FlowDefStore getFlowDefStore() {
        return flowDefStore;
    }

    public ISpanSender getSpanSender() {
        return spanSender;
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
        FlowCaseEntity flowCase = caseStore.loadById(taskEntity.getFlowCaseId())
                                           .orElseThrow(()->new UserException("undefined flowId "+taskEntity.getFlowCaseId()));
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));

        finishTask0(flowDef, flowCase, taskEntity, statusCode, response);
    }

    private void finishTask0(FlowDef flowDef, FlowCaseEntity flowCase, FlowTaskEntity task, int statusCode, Object response) {

        LOGGER.debug("finishTask {}/{} - {} - status {} {}", flowCase.getCaseType(), flowCase.getId(), task.getId(), statusCode, response);

        if(task.getFinished()!=null){
            throw new UserException("task is finished "+ task.getId());
        }
        task.setFinished(Instant.now());

        String error = null;
        if(statusCode >=200 && statusCode <=202){
            taskStore.finishOk(task.getId(), statusCode, response);
        } else {
            error = response !=null ? response.toString() : "";
            taskStore.finishError(task.getId(), statusCode, error);
        }

        FlowWorkerDef workerDef = flowDefStore.cacheWorker(flowDef, task.getStep(), task.getWorker());
        spanSender.finishRunningTask(flowCase, task, workerDef, statusCode, error);
        spanSender.finishTask(flowCase, task, workerDef);
        flowThreadPool.addCase(task.getFlowCaseId(), task.getId());
    }

    private void nextTick(String flowCaseId) {

        FlowCaseEntity flowCase = caseStore.loadById(flowCaseId)
                                      .orElseThrow(()->new UserException("invalid flowCaseId "+flowCaseId));
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid flowCaseType id="+flowCase.getId()+", type="+flowCase.getCaseType()));
        List<FlowTaskEntity> tasks = taskStore.selectTaskByCaseId(flowCase.getId());

        String aktStepCode = openTasks(flowDef, flowCase, tasks);
        if(aktStepCode!=null){
            IFlowExecutor executor = flowExecutorMap.get(flowDef.getExecutor());
            executor.execTick(flowDef, flowCase, tasks, aktStepCode, new IFlowExecutor.IFlowBack() {
                @Override
                public void sentTask(FlowTaskEntity task) {
                    taskStore.startRunning(task.getId());
                    task.setStarted(Instant.now());
                    spanSender.finishWaitingTask(flowCase, task, null);
                }

                @Override
                public void finishTask(FlowTaskEntity task, int statusCode, Object response) {
                    finishTask0(flowDef, flowCase, task, statusCode, response);
                }
            });
        }
    }

    private String openTasks(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {

        String aktState = FlowCaseManager.stepFromState(flowCase.getState());
        FlowStepDef aktStep = aktState!=null ? flowDef.getSteps().stream().filter(s-> Objects.equals(s.getCode(), aktState)).findFirst().orElse(null) : null;

        if(aktStep!=null) {
            String step = aktStep.getCode();
            List<FlowTaskEntity> openTasks = tasks
                    .stream()
                    .filter(t -> Objects.equals(step, t.getStep()) && t.getStarted() == null)
                    .toList();
            if (!openTasks.isEmpty()) {
                //mam rozpracovany step, pokracujem v spracovani
                return aktStep.getCode();
            }

            List<FlowTaskEntity> stepAllTasks = tasks
                    .stream()
                    .filter(t -> Objects.equals(step, t.getStep()))
                    .toList();
            if (stepAllTasks.size() == 1 && stepAllTasks.getFirst().getWorker().equals(FlowTask.STEP_ITERATOR)) {
                FlowTaskEntity task1 = stepAllTasks.getFirst();
                if (task1.getFinished() != null) {
                    //mame hotovy iterator, treba vytvorit tasky
                    int size = 0;
                    if (task1.getResponse() instanceof List<?> l) size = l.size();
                    if(size>0){
                        for (int i = 0; i < size; i++) {
                            tasks.addAll(createTasks(aktStep, flowCase.getId(), i));
                        }
                        return aktStep.getCode();
                    } else {
                        LOGGER.info("step with empty iterator {}/{}", flowCase.getId(), step);
                        //pokracujem dalej, takze pojdem na next
                    }
                } else {
                    //stale cakame na iterator, je startnuty ale nie je odpoved
                    return null;
                }
            }
            //nemam rozpracovane tasky, idem na dalsi step
        }
        FlowStepDef next = nextStep(flowCase, flowDef);
        if(next!=null){
            //spustam novy step
            caseStore.updateFlowState(flowCase.getId(), FlowCase.STEP_STATE_PREF+next.getCode());
            if(next.getItemsExpr()!=null){
                //spustam iterator
                FlowTaskEntity taskEntity = new FlowTaskEntity(ID.newId(), flowCase.getId(), next.getCode(), FlowTask.STEP_ITERATOR, -1);
                taskStore.insert(taskEntity);
                tasks.add(taskEntity);
                return next.getCode();
            }
            //vytvaram tasky v novom stepe bez iteratora
            tasks.addAll(createTasks(next, flowCase.getId(), 0));
            return next.getCode();
        }
        //uz nemam next, koncim
        finishFlow(flowCase, flowDef, tasks);
        return null;
    }

    private List<FlowTaskEntity> createTasks(FlowStepDef step, String caseId, int stepIndex) {
        List<FlowTaskEntity> tasks = new ArrayList<>();
        for (FlowWorkerDef worker : step.getWorkers()){
            FlowTaskEntity taskEntity = new FlowTaskEntity(ID.newId(), caseId, step.getCode(), worker.getCode(), stepIndex);
            taskStore.insert(taskEntity);
            tasks.add(taskEntity);
        }
        return tasks;
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

    private void finishFlow(FlowCaseEntity flowCaseEntity, FlowDef flowDef, List<FlowTaskEntity> tasks) {

        FlowTaskEntity finishTask = tasks.stream().filter(t->t.getWorker().equals(FlowTask.FLOW_RESPONSE)).findFirst().orElse(null);
        if(finishTask!=null && finishTask.getResponse()!=null){
            flowCaseEntity.setResponse(finishTask.getResponse());
            tasks.remove(finishTask);
        }
        flowCaseEntity.setFinished(Instant.now());
        flowCaseEntity.setState(FlowCase.FINISHED);
        caseStore.finishFlow(flowCaseEntity.getId(), flowCaseEntity.getResponse());

        LOGGER.info("finish flow {}/{} {}", flowCaseEntity.getCaseType(), flowCaseEntity.getId(), flowCaseEntity.getResponse());

        //save to blob and clean db
        FlowCase flowCase = caseStore.loadFlowCaseById(flowCaseEntity.getId());
        flowCase.setTasks(taskStore.selectFlowTaskByCaseId(flowCaseEntity.getId()));
        Callback callback = flowCase.getCallback();
        flowCase.setCallback(null);
        try{
            blobStore.saveFinalCase(flowCaseEntity.getCaseType(), flowCase.getId(), flowCase);
            spanSender.finishFlow(flowCaseEntity, flowDef, null);
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

    public void reprocess(String caseId, List<String> removeTasks) {
    }
}
