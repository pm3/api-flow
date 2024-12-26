package eu.aston;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.ognl.OgnlFlow;
import eu.aston.flow.ognl.OgnlFlowFactory;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import org.junit.jupiter.api.Test;

public class OgnlFlow2Test {

    static FlowTaskEntity createTaskOk(TaskHttpRequest request, FlowTaskEntity task, ObjectMapper objectMapper) throws Exception {
        task.setResponseCode(200);
        if(request.body()==null){
            task.setResponse(new HashMap<>());
        } else {
            task.setResponse(objectMapper.readValue(request.body(), Object.class));
        }
        task.setCreated(Instant.now());
        task.setFinished(Instant.now());
        return task;
    }

    @Test
    public void loadFlow() throws Exception {

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        AppConfig appConfig = new AppConfig();
        OgnlFlowFactory ognlFlowFactory = new OgnlFlowFactory(objectMapper, appConfig);
        OgnlFlow ognlFlow = (OgnlFlow) ognlFlowFactory.createFlow(new File("test_root/flow2.flow.yaml"));

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId("2");
        flowCase.setState(FlowCase.CREATED);
        flowCase.setParams(Map.of("a", 1, "b", "1", "items", List.of(1,2,3)));
        List<FlowTaskEntity> tasks = new ArrayList<>();
        for(int i=0; i<200; i++){
            List<TaskHttpRequest> requests = ognlFlow.execTick(flowCase, tasks);

            System.out.println("state "+flowCase.getState());
            System.out.println("tasks "+tasks);
            System.out.println("requests "+requests);

            if(flowCase.getState().equals(FlowCase.FINISHED)){
                break;
            }

            Map<String, FlowTaskEntity> taskMap = tasks.stream().collect(
                    Collectors.toMap(FlowTaskEntity::getId, Function.identity()));
            for (TaskHttpRequest request : requests) {
                FlowTaskEntity task = taskMap.get(request.taskId());
                if (task != null) {
                    createTaskOk(request, task, objectMapper);
                    if(task.getWorker().equals("response")){
                        flowCase.setResponse(task.getResponse());
                    }
                } else {
                    System.out.println("task not found " + request.taskId());
                }
            }
            tasks.removeIf(t-> t.getFinished()==null);
        }
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(flowCase));
    }

}
