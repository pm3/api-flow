package eu.aston.flow.nodejs;

import java.io.File;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.IFlowExecutor;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.header.CallbackRunner;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NodeJsFlowExecutor implements IFlowExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeJsFlowExecutor.class);

    public static final String ID = "node";

    private final CallbackRunner callbackRunner;
    private final ObjectMapper objectMapper;
    private final Map<String, byte[]> flowCodeMap = new ConcurrentHashMap<>();

    public NodeJsFlowExecutor(CallbackRunner callbackRunner, ObjectMapper objectMapper) {
        this.callbackRunner = callbackRunner;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<TaskHttpRequest> execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks,
                                          String stepCode) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(new NodeJsFlowRequest(flowDef, flowCase, tasks, stepCode));
            HttpResponse<String> response = callbackRunner.call("POST", URI.create("http://node:3000/case"),
                                                                Map.of("Content-Type", "application/json"), body,
                                                                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400 && response.body().contains("flow-not-found")) {
                LOGGER.error("execTick error {}", response.body());
                parseFlowScript(flowDef.getCode(), flowCodeMap.get(flowDef.getCode()));
                response = callbackRunner.call("POST", URI.create("http://node:3000/case"),
                                               Map.of("Content-Type", "application/json"), body,
                                               HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(List.class,TaskHttpRequest.class));
            }
            LOGGER.error("execTick error {}", response.body());
        } catch (Exception e) {
            LOGGER.error("execTick error", e);
        }
        return null;
    }

    public FlowDef initJsFlowDef(File fileJs) {
        try {
            byte[] body = Files.readAllBytes(fileJs.toPath());
            FlowDef flowDef = parseFlowScript(fileJs.getName().split("\\.")[0], body);
            if (flowDef != null) {
                return flowDef;
            }
        } catch (Exception e) {
            LOGGER.error("initJsFlowDef error", e);
        }
        return null;
    }

    private FlowDef parseFlowScript(String code, byte[] body) throws Exception {
        HttpResponse<String> response = callbackRunner.call("POST", URI.create("http://node:3000/flow"), null, body,
                                                            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            FlowDef flowDef = objectMapper.readValue(response.body(), FlowDef.class);
            flowDef.setExecutor(ID);
            flowDef.setCode(code);
            flowCodeMap.put(flowDef.getCode(), body);
            return flowDef;
        }
        LOGGER.error("initJsFlowDef error {}", response.body());
        return null;
    }
}