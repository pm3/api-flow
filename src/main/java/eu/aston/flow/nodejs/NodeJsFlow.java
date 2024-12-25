package eu.aston.flow.nodejs;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.def.IFlowDef;
import eu.aston.flow.def.JwtIssuerDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import eu.aston.header.CallbackRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeJsFlow implements IFlowDef {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeJsFlow.class);

    private NodeJsFlowData flowData;
    private Map<String, FlowWorkerDef> workerMap;
    private final String code;
    private final byte[] flowJs;
    private final CallbackRunner callbackRunner;
    private final ObjectMapper objectMapper;
    private final String nodeUri;

    public NodeJsFlow(String code, byte[] flowJs, CallbackRunner callbackRunner, ObjectMapper objectMapper,
                      String nodeUri) throws Exception {
        this.code = code;
        this.flowJs = flowJs;
        this.callbackRunner = callbackRunner;
        this.objectMapper = objectMapper;
        this.nodeUri = nodeUri;
        parseFlowScript();
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
    public FlowWorkerDef worker(String name) {
        return workerMap.get(name);
    }

    @Override
    public List<TaskHttpRequest> execTick(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(new NodeJsFlowRequest(flowCase, tasks));
            HttpResponse<String> response = callbackRunner.call("POST", URI.create(nodeUri+"/case"),
                                                                Map.of("Content-Type", "application/json"), body,
                                                                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(List.class,TaskHttpRequest.class));
            }
            LOGGER.error("execTick error {}", response.body());
        } catch (Exception e) {
            LOGGER.error("execTick error", e);
        }
        return null;
    }

    private void parseFlowScript() throws Exception {
        HttpResponse<String> response = callbackRunner.call("POST", URI.create(nodeUri+"/flow"), null, flowJs,
                                                            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            this.flowData = objectMapper.readValue(response.body(), NodeJsFlowData.class);
            this.workerMap = flowData.workers().stream().collect(Collectors.toMap(FlowWorkerDef::getName, Function.identity()));
            return;
        }
        throw new Exception("initJsFlowDef error "+response.body());
    }
}
