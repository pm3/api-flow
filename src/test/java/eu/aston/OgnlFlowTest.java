package eu.aston;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.ognl.OgnlFlow;
import eu.aston.flow.ognl.OgnlFlowFactory;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.utils.ID;
import org.junit.jupiter.api.Test;

public class OgnlFlowTest {

    static int counter = 0;

    static FlowTaskEntity createTaskOk(String worker1, int index, Map<String, String> data) {
        FlowTaskEntity task = new FlowTaskEntity();
        task.setId(String.valueOf(++counter));
        task.setFlowCaseId("1");
        task.setWorker(worker1);
        task.setStepIndex(index);
        task.setResponseCode(200);
        task.setResponse(data);
        task.setTimeout(100);
        task.setCreated(Instant.now());
        task.setFinished(Instant.now());
        return task;
    }


    static void testTick(String state, List<FlowTaskEntity> tasks) throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        AppConfig appConfig = new AppConfig();
        OgnlFlowFactory ognlFlowFactory = new OgnlFlowFactory(objectMapper, appConfig);
        OgnlFlow ognlFlow = (OgnlFlow) ognlFlowFactory.createFlow(new File("test_root/flow1.flow.yaml"));

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId("1");
        flowCase.setState(state);
        flowCase.setParams(new HashMap<>());
        List<TaskHttpRequest> requests = ognlFlow.execTick(flowCase, tasks);
        System.out.println("tasks");
        System.out.println(tasks.size());
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tasks));
        System.out.println("requests");
        System.out.println(requests.size());
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requests));
        System.out.println("state "+flowCase.getState());

    }

    @Test
    public void loadFlow1() throws Exception {
        List<FlowTaskEntity> tasks = new ArrayList<>();
        testTick(FlowCase.CREATED, tasks);
    }

    @Test
    public void loadFlow2() throws Exception {
        List<FlowTaskEntity> tasks = new ArrayList<>();
        tasks.add(createTaskOk("worker_1", 0, Map.of("a", "1", "c", "c")));
        testTick("worker_1", tasks);
    }

    @Test
    public void loadFlow3() throws Exception {
        List<FlowTaskEntity> tasks = new ArrayList<>();
        tasks.add(createTaskOk("worker_1", 0, Map.of("a", "1", "c", "c")));
        tasks.add(createTaskOk("step2/worker_21", 0, Map.of("a", "c", "b", "2")));
        testTick("step2", tasks);
    }

    @Test
    public void loadFlow4() throws Exception {
        List<FlowTaskEntity> tasks = new ArrayList<>();
        tasks.add(createTaskOk("worker_1", 0, Map.of("a", "1", "c", "c")));
        tasks.add(createTaskOk("step2/worker_21", 0, Map.of("a", "c", "b", "2")));
        testTick("step2", tasks);
    }

    static FlowTaskEntity createTaskOk(TaskHttpRequest request, FlowTaskEntity task, ObjectMapper objectMapper) throws Exception {
        task.setResponseCode(200);
        if(request.body()==null){
            task.setResponse(new HashMap<>());
        } else {
            task.setResponse(objectMapper.readValue(request.body(), Map.class));
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
        OgnlFlow ognlFlow = (OgnlFlow) ognlFlowFactory.createFlow(new File("test_root/flow1.flow.yaml"));

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId("1");
        flowCase.setState(FlowCase.CREATED);
        flowCase.setParams(Map.of("a", 1, "b", "1"));
        List<FlowTaskEntity> tasks = new ArrayList<>();
        for(int i=0; i<200; i++){
            List<TaskHttpRequest> requests = ognlFlow.execTick(flowCase, new ArrayList<>(tasks));

            System.out.println("state "+flowCase.getState());
            System.out.println("tasks "+tasks);
            System.out.println("requests "+requests);

            if(flowCase.getState().equals(FlowCase.FINISHED)){
                break;
            }

            for (TaskHttpRequest request : requests) {
                FlowTaskEntity task = new FlowTaskEntity(ID.newId(), flowCase.getId(), request.worker(), request.stepIndex());
                task.setCreated(Instant.now());
                createTaskOk(request, task, objectMapper);
                tasks.add(task);
                if(task.getWorker().equals("response")){
                    flowCase.setResponse(task.getResponse());
                }
                flowCase.setState(request.step());
            }
        }
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(flowCase));
    }

}
