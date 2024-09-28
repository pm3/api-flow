package eu.aston.flow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import eu.aston.flow.model.FlowCase;
import eu.aston.utils.SuperTimer;
import jakarta.inject.Singleton;

@Singleton
public class WaitingFlowCaseManager {

    private final SuperTimer timer;
    private final ConcurrentHashMap<String, WaitingFlow> map = new ConcurrentHashMap<>();

    public WaitingFlowCaseManager(SuperTimer timer) {
        this.timer = timer;
    }

    public void waitFinish(FlowCase flowCase, CompletableFuture<FlowCase> future, int waitTimeSeconds) {
        WaitingFlow waitingFlow = new WaitingFlow(flowCase, future);
        map.put(flowCase.getId(), waitingFlow);
        timer.schedule(waitTimeSeconds*1000L, flowCase.getId(), this::removeFlow);
    }

    public void finished(FlowCase flowCaseFinished){
        WaitingFlow waitingFlow = map.remove(flowCaseFinished.getId());
        if(waitingFlow!=null){
            waitingFlow.future().complete(flowCaseFinished);
        }
    }

    private void removeFlow(String id) {
        WaitingFlow waitingFlow = map.remove(id);
        if(waitingFlow!=null){
            waitingFlow.future().complete(waitingFlow.flowCase());
        }
    }

    private record WaitingFlow(FlowCase flowCase, CompletableFuture<FlowCase> future){}
}
