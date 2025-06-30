package eu.aston.flow;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowStepDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.nodejs.NodeJsFlowExecutor;
import eu.aston.flow.ognl.YamlOgnlFlowExecutor;
import eu.aston.user.AuthException;
import eu.aston.user.UserException;
import eu.aston.utils.BaseValid;
import eu.aston.utils.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowDefStore {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowDefStore.class);

    private final Map<String, FlowDef> flowsMap = new ConcurrentHashMap<>();
    private final Map<String, FlowWorkerDef> workerMap = new ConcurrentHashMap<>();
    private final Set<String> apiKeySet = new HashSet<>();
    private final ObjectMapper yamlObjectMapper;
    private final NodeJsFlowExecutor nodeJsFlowExecutor;
    private Integer defaultTimeout;

    public FlowDefStore(HttpClient httpClient, ObjectMapper objectMapper, NodeJsFlowExecutor nodeJsFlowExecutor) {
        this.nodeJsFlowExecutor = nodeJsFlowExecutor;
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
        yamlObjectMapper.registerModule(new JavaTimeModule());
    }

    public Optional<FlowDef> flowDef(String type){
        return Optional.ofNullable(flowsMap.get(type));
    }

    public void loadRoot(File rootDir, boolean clear) {
        LOGGER.info("start reload configs, clear {}", clear);
        if(!rootDir.isDirectory()) {
            throw new UserException("invalid root dir "+rootDir.getAbsolutePath());
        }
        if(clear) {
            flowsMap.clear();
            apiKeySet.clear();
        }
        for(File f : Objects.requireNonNull(rootDir.listFiles())){
            if(f.isFile() && f.getName().endsWith(".flow.yaml")){
                try{
                    LOGGER.info("start load flow yaml {}", f.getName());
                    FlowDef flowDef = yamlObjectMapper.readValue(f, FlowDef.class);
                    flowDef.setExecutor(YamlOgnlFlowExecutor.ID);
                    loadFlow(f, flowDef, true);
                }catch (Exception e){
                    LOGGER.warn("ignore flow file yaml {} - {}", f.getAbsolutePath(), e.getMessage());
                }
            }
            if(f.isFile() && f.getName().endsWith(".flow.js")){
                try{
                    LOGGER.info("start load flow js {}", f.getName());
                    FlowDef flowDef = nodeJsFlowExecutor.initJsFlowDef(f);
                    if(flowDef!=null){
                        flowDef.setExecutor(NodeJsFlowExecutor.ID);
                        loadFlow(f, flowDef, false);
                    }
                }catch (Exception e){
                    LOGGER.warn("ignore flow file js {} - {}", f.getAbsolutePath(), e.getMessage());
                }
            }
        }

    }

    private void loadFlow(File flowFile, FlowDef flowDef, boolean fullConfig) {
        BaseValid.require(flowDef, "flow");
        BaseValid.code(flowDef.getCode(), "flow.code", "-");

        if (flowDef.getApiKeys() != null && !flowDef.getApiKeys().isEmpty()) {
            for(String apiKey : flowDef.getApiKeys()) {
                apiKeySet.add(flowDef.getCode()+":"+apiKey);
            }
        } else {
            apiKeySet.add(flowDef.getCode()+":no-auth");
        }

        if(flowDef.getSteps()==null || flowDef.getSteps().isEmpty()){
            throw new UserException("empty flow.steps");
        }
        for(FlowStepDef step : flowDef.getSteps()){
            BaseValid.require(step, "step");
            BaseValid.code(step.getCode(), "step.code", "_");
            if(step.getWorkers()==null || step.getWorkers().isEmpty()){
                throw new UserException("empty step.workers");
            }
            FlowWorkerDef iterator = null;
            for(FlowWorkerDef worker : step.getWorkers()){
                BaseValid.require(worker, "worker");
                BaseValid.code(worker.getCode(), "worker.code", "_");
                if(worker.getCode().equals(FlowTask.STEP_ITERATOR)){
                    iterator = worker;
                }
                if(worker.getMethod()==null && worker.getParams()!=null && !worker.getParams().isEmpty()){
                    worker.setMethod("POST");
                }
                if(fullConfig){
                    BaseValid.str(worker.getPath(), -1, 512,"worker.path");
                    BaseValid.str(worker.getPathExpr(), -1, 512,"worker.$path");
                    if(worker.getPath()==null && worker.getPathExpr()==null){
                        worker.setPath(FlowTask.ECHO);
                    }
                }
            }
            if(step.getItemsExpr()!=null && iterator==null){
                iterator = new FlowWorkerDef();
                iterator.setCode(FlowTask.STEP_ITERATOR);
                iterator.setPath(FlowTask.ECHO);
                iterator.setParams(new HashMap<>());
                iterator.getParams().put("$.", step.getItemsExpr());
                step.getWorkers().addFirst(iterator);
            } else if(step.getItemsExpr()!=null && iterator!=null){
                step.setItemsExpr(FlowTask.STEP_ITERATOR);
            }
        }
        if(flowDef.getResponse()!=null){
            FlowWorkerDef w = new FlowWorkerDef();
            w.setCode(FlowTask.FLOW_RESPONSE);
            w.setPath(FlowTask.ECHO);
            w.setParams(flowDef.getResponse());

            FlowStepDef responseStep = new FlowStepDef();
            responseStep.setCode(FlowTask.FLOW_RESPONSE);
            responseStep.setWorkers(new ArrayList<>());
            responseStep.getWorkers().add(w);
            flowDef.getSteps().add(responseStep);
            flowDef.setResponse(null);
        }
        flowsMap.put(flowDef.getCode(), flowDef);
    }

    public FlowWorkerDef cacheWorker(FlowDef flowDef, String step, String worker) {
        String key = flowDef.getCode()+"|"+step+"|"+worker;
        FlowWorkerDef workerDef = workerMap.get(key);
        if(workerDef==null){
            for(FlowStepDef step2 : flowDef.getSteps()){
                for(FlowWorkerDef worker2 : step2.getWorkers()){
                    String key2 = flowDef.getCode()+"|"+step2.getCode()+"|"+worker2.getCode();
                    workerMap.put(key2, worker2);
                }
            }
            workerDef = workerMap.get(key);
        }
        return workerDef;
    }

    public void checkCaseAuth(FlowDef flowDef, String apiKey) {
        if(!apiKeySet.contains(flowDef.getCode()+":no-auth"))
        {
            if(apiKey==null) {
                throw new AuthException("case access denied, no api key", true);
            }
            String hash = Hash.sha2(apiKey.getBytes(StandardCharsets.UTF_8));
            if(!apiKeySet.contains(flowDef.getCode()+":"+hash)){
                throw new AuthException("case access denied", true);
            }
        }
    }

    public Integer getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Integer defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
}
