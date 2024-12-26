package eu.aston.flow.nodejs;

import java.io.File;
import java.nio.file.Files;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.IFlowDef;
import eu.aston.flow.IFlowFactory;
import eu.aston.header.CallbackRunner;
import jakarta.inject.Singleton;

@Singleton
public class NodeJsFlowFactory implements IFlowFactory {

    public static final String FLOW_JS_SUFFIX = ".flow.js";
    private final CallbackRunner callbackRunner;
    private final ObjectMapper objectMapper;

    public NodeJsFlowFactory(CallbackRunner callbackRunner, ObjectMapper objectMapper) {
        this.callbackRunner = callbackRunner;
        this.objectMapper = objectMapper;
    }

    @Override
    public IFlowDef createFlow(File f) throws Exception {
        String n = f.getName();
        if(n.endsWith(FLOW_JS_SUFFIX)){
            String code = n.substring(0, n.length()- FLOW_JS_SUFFIX.length());
            byte[] flowJs = Files.readAllBytes(f.toPath());
            return new NodeJsFlow(code, flowJs,
                                  callbackRunner, objectMapper,
                                  "http://node:3000");
        }
        return null;
    }
}
