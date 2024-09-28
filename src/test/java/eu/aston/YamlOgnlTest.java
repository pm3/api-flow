package eu.aston;

import java.io.File;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import eu.aston.flow.FlowDefStore;
import eu.aston.flow.IFlowBridge;
import eu.aston.flow.IFlowExecutor;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.ognl.YamlOgnlFlowExecutor;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.header.CallbackRunner;
import eu.aston.utils.ID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class YamlOgnlTest {

    private static FlowDefStore flowDefStore;
    private static ObjectMapper objectMapper;
    private static AppConfig appConfig;

    @BeforeAll
    public static void startServer() throws Exception {

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        HttpResponse<Object> response1 = Mockito.mock(HttpResponse.class);
        Mockito.when(response1.body()).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        Mockito.when(httpClient.send(Mockito.any(), Mockito.any())).thenReturn(response1);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        flowDefStore = new FlowDefStore(httpClient, objectMapper, null, null);
        appConfig = new AppConfig();
        appConfig.setWorkerApiKey("123");
        appConfig.setAppHost("http://localhost:8080");

        flowDefStore.loadRoot(new File("src/test/resources/ognl"), true);
    }

    @Test
    public void testLoadFlow(){
        Optional<FlowDef> flow1 = flowDefStore.flowDef("echo_flow");
        Assertions.assertTrue(flow1.isPresent());
        Assertions.assertEquals(3, flow1.get().getSteps().size());
    }

    @Test
    public void testEcho(){
        FlowDef flow1 = flowDefStore.flowDef("echo_flow")
                                    .orElseThrow(()-> new RuntimeException("undefined flow"));

        CallbackRunner callbackRunner = Mockito.mock(CallbackRunner.class);
        YamlOgnlFlowExecutor yamlOgnlFlowExecutor = new YamlOgnlFlowExecutor(flowDefStore, null, appConfig, objectMapper, callbackRunner);
        IFlowExecutor.IFlowBack flowBack = Mockito.mock(IFlowExecutor.IFlowBack.class);

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(ID.newId());
        flowCase.setCaseType(flow1.getCode());
        flowCase.setState("step-step1");
        flowCase.setParams(new HashMap<>());
        flowCase.getParams().put("a", "a");
        flowCase.getParams().put("b", "b");
        List<FlowTaskEntity> tasks = new ArrayList<>();
        tasks.add(new FlowTaskEntity("1", flowCase.getId(), "step1", "worker_echo_local", 0));
        yamlOgnlFlowExecutor.execTick(flow1, flowCase, tasks, "step1", flowBack);
        Mockito.verify(flowBack).finishTask(tasks.getFirst(), 200, Map.of("a", "a", "b", "b"));
    }

    @Test
    public void testBlocked() throws Exception {


        CallbackRunner callbackRunner = Mockito.mock();
        HttpResponse<Object> response = Mockito.mock();
        Mockito.when(response.statusCode()).thenReturn(200);
        Mockito.when(response.body()).thenReturn("{\"a\":\"a\"}".getBytes(StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<Object>> future = new CompletableFuture<>();
        future.complete(response);
        Mockito.when(callbackRunner.callAsync(Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(future);
        YamlOgnlFlowExecutor yamlOgnlFlowExecutor = new YamlOgnlFlowExecutor(flowDefStore, null, appConfig, objectMapper, callbackRunner);

        FlowDef flow1 = flowDefStore.flowDef("echo_flow")
                                    .orElseThrow(()-> new RuntimeException("undefined flow"));

        IFlowExecutor.IFlowBack flowBack = Mockito.mock(IFlowExecutor.IFlowBack.class);

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(ID.newId());
        flowCase.setCaseType(flow1.getCode());
        flowCase.setState("step-step2");
        flowCase.setParams(new HashMap<>());
        flowCase.getParams().put("a", "a");
        flowCase.getParams().put("b", "b");
        List<FlowTaskEntity> tasks = new ArrayList<>();
        FlowTaskEntity t0 = new FlowTaskEntity("1", flowCase.getId(), "step1", "worker_echo_block", 0);
        t0.setFinished(Instant.now());
        t0.setResponse(Map.of("c", "c"));
        tasks.add(t0);
        tasks.add(new FlowTaskEntity(ID.newId(), flowCase.getId(), "step2", "worker_echo_block", 0));
        yamlOgnlFlowExecutor.execTick(flow1, flowCase, tasks, "step2", flowBack);

        Mockito.verify(flowBack).finishTask(tasks.get(1), 200, Map.of("a", "a"));
    }

    @Test
    public void testBlockedError() throws Exception {

        CallbackRunner callbackRunner = Mockito.mock();
        HttpResponse<Object> response = Mockito.mock();
        Mockito.when(response.statusCode()).thenReturn(400);
        Mockito.when(response.body()).thenReturn("error400".getBytes(StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<Object>> future = new CompletableFuture<>();
        future.complete(response);
        Mockito.when(callbackRunner.callAsync(Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(future);
        YamlOgnlFlowExecutor yamlOgnlFlowExecutor = new YamlOgnlFlowExecutor(flowDefStore, null, appConfig, objectMapper, callbackRunner);

        FlowDef flow1 = flowDefStore.flowDef("echo_flow")
                                    .orElseThrow(()-> new RuntimeException("undefined flow"));

        IFlowExecutor.IFlowBack flowBack = Mockito.mock(IFlowExecutor.IFlowBack.class);

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(ID.newId());
        flowCase.setCaseType(flow1.getCode());
        flowCase.setState("step-step2");
        flowCase.setParams(new HashMap<>());
        flowCase.getParams().put("a", "a");
        flowCase.getParams().put("b", "b");
        List<FlowTaskEntity> tasks = new ArrayList<>();
        FlowTaskEntity t0 = new FlowTaskEntity("1", flowCase.getId(), "step1", "worker_echo_local", 0);
        t0.setFinished(Instant.now());
        t0.setResponse(Map.of("c", "c"));
        tasks.add(t0);
        tasks.add(new FlowTaskEntity(ID.newId(), flowCase.getId(), "step2", "worker_echo_block", 0));
        yamlOgnlFlowExecutor.execTick(flow1, flowCase, tasks, "step2", flowBack);

        Mockito.verify(flowBack).finishTask(tasks.get(1), 400, "error400");
    }

    @Test
    public void testBlockedQueue() throws Exception {

        IFlowBridge flowBridge = Mockito.mock(IFlowBridge.class);
        CallbackRunner callbackRunner = Mockito.mock(CallbackRunner.class);
        YamlOgnlFlowExecutor yamlOgnlFlowExecutor = new YamlOgnlFlowExecutor(flowDefStore, flowBridge, appConfig, objectMapper, callbackRunner);

        FlowDef flow1 = flowDefStore.flowDef("echo_flow")
                                    .orElseThrow(()-> new RuntimeException("undefined flow"));

        IFlowExecutor.IFlowBack flowBack = Mockito.mock(IFlowExecutor.IFlowBack.class);

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(ID.newId());
        flowCase.setCaseType(flow1.getCode());
        flowCase.setState("step-step2");
        flowCase.setParams(new HashMap<>());
        flowCase.getParams().put("a", "a");
        flowCase.getParams().put("b", "b");
        List<FlowTaskEntity> tasks = new ArrayList<>();
        FlowTaskEntity t0 = new FlowTaskEntity("1", flowCase.getId(), "step1", "worker_echo_local", 0);
        t0.setFinished(Instant.now());
        t0.setResponse(Map.of("c", "c"));
        tasks.add(t0);
        tasks.add(new FlowTaskEntity(ID.newId(), flowCase.getId(), "step2", "worker_echo_block", 0));
        yamlOgnlFlowExecutor.execTick(flow1, flowCase, tasks, "step2", flowBack);

        Mockito.verify(flowBridge).sendQueueEvent(flowCase.getId(),
                                                  tasks.get(1).getId(),
                                                  "POST",
                                                  "http://localhost:8089/echo",
                                                  null,
                                                  "{\"a\":\"a\",\"b\":\"b\",\"c\":\"c\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testAsync() throws Exception{

        CallbackRunner callbackRunner = Mockito.mock(CallbackRunner.class);
        YamlOgnlFlowExecutor yamlOgnlFlowExecutor = new YamlOgnlFlowExecutor(flowDefStore, null, appConfig, objectMapper, callbackRunner);

        FlowDef flow1 = flowDefStore.flowDef("echo_flow")
                                    .orElseThrow(()-> new RuntimeException("undefined flow"));

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId(ID.newId());
        flowCase.setCaseType(flow1.getCode());
        flowCase.setState("step-step3");
        flowCase.setParams(new HashMap<>());
        flowCase.getParams().put("a", "a");
        flowCase.getParams().put("b", "b");
        List<FlowTaskEntity> tasks = new ArrayList<>();
        tasks.add(new FlowTaskEntity(ID.newId(), flowCase.getId(), "step3", "worker_echo_async", 0));
        yamlOgnlFlowExecutor.execTick(flow1, flowCase, tasks, "step3", new IFlowExecutor.IFlowBack() {
            @Override
            public void sentTask(FlowTaskEntity task) {
                System.out.println("testAsync sentTas");
            }

            @Override
            public void finishTask(FlowTaskEntity task, int statusCode, Object response) {
                System.out.println("testAsync "+ response);
            }
        });
        Thread.sleep(1000);
        System.out.println(objectMapper.writeValueAsString(tasks.getFirst()));
    }
}
