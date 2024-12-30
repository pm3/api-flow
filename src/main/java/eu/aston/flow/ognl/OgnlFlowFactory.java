package eu.aston.flow.ognl;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.aston.AppConfig;
import eu.aston.flow.IFlowDef;
import eu.aston.flow.IFlowFactory;
import eu.aston.flow.def.CronJob;
import eu.aston.user.UserException;
import jakarta.inject.Singleton;

@Singleton
public class OgnlFlowFactory implements IFlowFactory {

    public static final String FLOW_YAML_SUFFIX = ".flow.yaml";

    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlObjectMapper;
    private final int defaultTimeout;

    public OgnlFlowFactory(ObjectMapper objectMapper, AppConfig appConfig) {
        this.objectMapper = objectMapper;
        this.yamlObjectMapper = new ObjectMapper(new YAMLFactory());
        yamlObjectMapper.registerModule(new JavaTimeModule());
        this.defaultTimeout = appConfig.getDefaultTimeout() >0 ? appConfig.getDefaultTimeout() : 30;
    }

    @Override
    public IFlowDef createFlow(File f) throws Exception {
        String n = f.getName();
        if(n.endsWith(FLOW_YAML_SUFFIX)) {
            String code = n.substring(0, n.length() - FLOW_YAML_SUFFIX.length());
            OgnlFlowData flowData = yamlObjectMapper.readValue(f, OgnlFlowData.class);
            validData(flowData);
            return new OgnlFlow(code, flowData, objectMapper, defaultTimeout);
        }
        return null;
    }

    private void validData(OgnlFlowData flowData) {
        if(flowData.workers()==null || flowData.workers().isEmpty()){
            throw new UserException("flow worker list is empty");
        }
        for(WorkerDef worker : flowData.workers()){
            if(worker.getName()==null){
                throw new UserException("worker name is null "+worker);
            }
            if(!worker.getName().matches("^[a-zA-Z][a-zA-Z0-9_]+$")
                    && !worker.getName().matches("^[a-zA-Z][a-zA-Z0-9_]+/[a-zA-Z][a-zA-Z0-9_]+$")
                    && !worker.getName().matches("^[a-zA-Z][a-zA-Z0-9_]+/_iterator$")
            ){
                throw new UserException("worker name is invalid "+worker.getName());
            }
            if(worker.getMethod()==null){
                worker.setMethod("POST");
            } else {
                worker.setMethod(worker.getMethod().toUpperCase());
            }
            if(worker.getPath()==null && worker.getPathExpr()==null){
                throw new UserException("worker path is null "+worker);
            }
            if(worker.getTimeout()==null){
                worker.setTimeout(defaultTimeout);
            }
        }
        if(flowData.cronJobs()!=null){
            for(CronJob cronJob : flowData.cronJobs()){
                if(cronJob.expression()==null){
                    throw new UserException("cron job expression is required");
                }
            }
        }
    }
}
