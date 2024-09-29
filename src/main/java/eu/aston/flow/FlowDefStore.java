package eu.aston.flow;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import eu.aston.flow.def.AuthDef;
import eu.aston.flow.def.BaseAuthDef;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowStepDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.def.JwtIssuerDef;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.nodejs.NodeJsFlowExecutor;
import eu.aston.flow.ognl.YamlOgnlFlowExecutor;
import eu.aston.user.AuthException;
import eu.aston.user.UserContext;
import eu.aston.user.UserException;
import eu.aston.utils.BaseValid;
import eu.aston.utils.Hash;
import eu.aston.utils.JwtVerify;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowDefStore {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowDefStore.class);

    private final JwtVerify jwtVerify;
    private final Map<String, FlowDef> flowsMap = new ConcurrentHashMap<>();
    private final Map<String, BaseAuthDef> baseAuthMap = new ConcurrentHashMap<>();
    private final Map<String, FlowWorkerDef> workerMap = new ConcurrentHashMap<>();
    private final ObjectMapper yamlObjectMapper;
    private final NodeJsFlowExecutor nodeJsFlowExecutor;
    private Integer defaultTimeout;

    public FlowDefStore(HttpClient httpClient, ObjectMapper objectMapper, JwtVerify jwtVerify, NodeJsFlowExecutor nodeJsFlowExecutor) {
        this.jwtVerify = jwtVerify;
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
            workerMap.clear();
            baseAuthMap.clear();
            if(jwtVerify!=null) jwtVerify.clear();
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
            if(f.isFile() && f.getName().endsWith(".auth.yaml")){
                try{
                    LOGGER.info("start load auth yaml {}", f.getName());
                    AuthDef authDef = yamlObjectMapper.readValue(f, AuthDef.class);
                    loadAuth(f, authDef);
                }catch (Exception e){
                    LOGGER.warn("ignore auth file yaml {} - {}", f.getAbsolutePath(), e.getMessage());
                }
            }
        }

    }

    private void loadFlow(File flowFile, FlowDef flowDef, boolean fullConfig) {
        BaseValid.require(flowDef, "flow");
        BaseValid.code(flowDef.getCode(), "flow.code");

        if(flowDef.getSteps()==null || flowDef.getSteps().isEmpty()){
            throw new UserException("empty flow.steps");
        }
        for(FlowStepDef step : flowDef.getSteps()){
            BaseValid.require(step, "step");
            BaseValid.code(step.getCode(), "step.code");
            if(step.getWorkers()==null || step.getWorkers().isEmpty()){
                throw new UserException("empty step.workers");
            }
            FlowWorkerDef iterator = null;
            for(FlowWorkerDef worker : step.getWorkers()){
                BaseValid.require(worker, "worker");
                BaseValid.code(worker.getCode(), "worker.code");
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

    public void loadAuth(File file, AuthDef authDef) {

        BaseValid.code(authDef.getCode(), "auth.code");
        if(authDef.getAdminUsers()==null) authDef.setAdminUsers(new ArrayList<>());
        if(authDef.getJwtIssuers()==null) authDef.setJwtIssuers(new ArrayList<>());
        if(authDef.getAdminUsers().isEmpty() && authDef.getJwtIssuers().isEmpty()){
            throw new UserException("tenant without users "+authDef.getCode());
        }

        for(BaseAuthDef baseAuth : authDef.getAdminUsers()){
            BaseValid.require(baseAuth, "baseAuth");
            BaseValid.require(baseAuth.getLogin(), "baseAuth.login");
            BaseValid.require(baseAuth.getPassword(), "baseAuth.password");
            baseAuth.setTenant(authDef.getCode());
            baseAuthMap.put(baseAuth.getLogin()+":"+baseAuth.getPassword(), baseAuth);
        }

        for(JwtIssuerDef issuer : authDef.getJwtIssuers()){
            BaseValid.require(issuer, "issuer");
            BaseValid.require(issuer.getIssuer(), "issuer.issuer");
            BaseValid.require(issuer.getAud(), "issuer.audience");
            BaseValid.require(issuer.getUrl(), "issuer.url");
            jwtVerify.addIssuer(issuer.getIssuer(), issuer.getAud(), issuer.getUrl(), authDef.getCode());
        }
    }

    public UserContext baseAuth(String user, String password){
        String key = user+":"+ Hash.sha2(password.getBytes(StandardCharsets.UTF_8));
        BaseAuthDef baseAuthDef = baseAuthMap.get(key);
        return baseAuthDef!=null ? new UserContext(baseAuthDef.getTenant(), baseAuthDef.getLogin()) : null;
    }

    public UserContext verifyJwt(DecodedJWT decodedJWT) throws Exception{
        return jwtVerify.verify(decodedJWT);
    }

    public void checkCaseAuth(String auth, UserContext userContext, String caseId) {
        if(auth!=null && !Objects.equals(auth, userContext.auth())){
            throw new AuthException("case access denied "+caseId, true);
        }
    }

    public Integer getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Integer defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
}
