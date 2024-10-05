package eu.aston.utils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowThreadPool {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowThreadPool.class);

    private final Executor executor;
    private final Consumer<String> flowExecutor;
    private final ConcurrentHashMap<String, String> flowsMap = new ConcurrentHashMap<>();

    public FlowThreadPool(int size, Consumer<String> flowExecutor) {
        this.executor = Executors.newFixedThreadPool(size);
        this.flowExecutor = flowExecutor;
    }

    public void addCase(String caseId, String taskId){
        if(flowsMap.put(caseId, taskId)==null){
            executor.execute(()->runFlow(caseId, taskId));
        }
    }

    private void runFlow(String caseId, String taskId){
        try{
            flowExecutor.accept(caseId);
        }catch (Exception e){
            LOGGER.warn("flow {} execute error {}", caseId, e.getMessage());
            LOGGER.warn("flow {} execute stack", caseId, e);
        }
        String lastTaskId = flowsMap.get(caseId);
        if(!Objects.equals(taskId, lastTaskId)){
            executor.execute(()->runFlow(caseId, lastTaskId));
        } else {
            flowsMap.remove(caseId, lastTaskId);
        }
    }
}
