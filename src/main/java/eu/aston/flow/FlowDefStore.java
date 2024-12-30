package eu.aston.flow;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.auth0.jwt.interfaces.DecodedJWT;
import eu.aston.flow.def.JwtIssuerDef;
import eu.aston.user.AuthException;
import eu.aston.user.UserContext;
import eu.aston.user.UserException;
import eu.aston.utils.BaseValid;
import eu.aston.utils.JwtVerify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowDefStore {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowDefStore.class);

    private final JwtVerify jwtVerify;
    private final Map<String, IFlowDef> flowsMap = new ConcurrentHashMap<>();
    private final Set<String> authMap = new HashSet<>();
    private Integer defaultTimeout;
    private final IFlowFactory[] flowFactories;

    public FlowDefStore(JwtVerify jwtVerify, IFlowFactory[] flowFactories) {
        this.jwtVerify = jwtVerify;
        this.flowFactories = flowFactories;
        LOGGER.info("factories {}", Stream.of(flowFactories).map(f -> f.getClass().getSimpleName()).reduce((a, b) -> a+", "+b).orElse(""));
    }

    public Optional<IFlowDef> flowDef(String type){
        return Optional.ofNullable(flowsMap.get(type));
    }

    public void loadRoot(File rootDir, boolean clear) {
        LOGGER.info("start reload configs, clear {}", rootDir.getAbsolutePath());
        if(!rootDir.isDirectory()) {
            throw new UserException("invalid root dir "+rootDir.getAbsolutePath());
        }
        if(clear) {
            flowsMap.clear();
            authMap.clear();
            if(jwtVerify!=null) jwtVerify.clear();
        }
        for(File f : Objects.requireNonNull(rootDir.listFiles())){
            loadFile(f);
        }

    }

    private void loadFile(File f) {
        for(IFlowFactory factory : flowFactories){
            try{
                IFlowDef def = factory.createFlow(f);
                if(def!=null){
                    loadFlow(f, def);
                    LOGGER.info("load flow {} file {}", def.getCode(), f.getAbsolutePath());
                    return;
                }
            }catch (Exception e){
                LOGGER.warn("ignore flow file {} - {}", f.getAbsolutePath(), e.getMessage());
            }
        }
        LOGGER.info("ignore file in config dir {}", f.getAbsolutePath());
    }

    private void loadFlow(File flowFile, IFlowDef flowDef) {
        BaseValid.code(flowDef.getCode(), "flow.code");
        flowsMap.put(flowDef.getCode(), flowDef);
        if(flowDef.getAuthApiKeys()!=null){
            for(String apiKey : flowDef.getAuthApiKeys()){
                authMap.add(flowDef.getCode()+"|"+apiKey);
            }
        }
        if(flowDef.getAuthJwtIssuers()!=null){
            for(JwtIssuerDef issuer : flowDef.getAuthJwtIssuers()){
                authMap.add(flowDef.getCode()+"|"+issuer.getIssuer()+"#"+issuer.getAud());
                jwtVerify.addIssuer(issuer.getIssuer(), issuer.getAud(), issuer.getUrl());
            }
        }
        if(flowDef.getAuthApiKeys()==null && flowDef.getAuthJwtIssuers()==null){
            authMap.add(flowDef.getCode()+"|*");
        }
    }

    public UserContext verifyJwt(DecodedJWT decodedJWT) throws Exception{
        return jwtVerify.verify(decodedJWT);
    }

    public void checkCaseAuth(IFlowDef flowDef, UserContext userContext) {
        String key = flowDef.getCode()+"|"+userContext.auth();
        if(!authMap.contains(key)){
            throw new AuthException("case access denied", true);
        }
    }

    public Integer getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Integer defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
}
