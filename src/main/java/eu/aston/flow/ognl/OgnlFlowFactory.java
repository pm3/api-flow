package eu.aston.flow.ognl;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.aston.AppConfig;
import eu.aston.flow.IFlowFactory;
import eu.aston.flow.IFlowDef;

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
            return new OgnlFlow(code, flowData, objectMapper, defaultTimeout);
        }
        return null;
    }
}
