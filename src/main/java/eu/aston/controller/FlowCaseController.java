package eu.aston.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.aston.blob.BlobStore;
import eu.aston.flow.FlowCaseManager;
import eu.aston.flow.FlowDefStore;
import eu.aston.flow.WaitingFlowCaseManager;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.model.ClearCase;
import eu.aston.flow.model.FlowAsset;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.FlowCaseCreate;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.model.IdValue;
import eu.aston.header.Callback;
import eu.aston.header.HeaderConverter;
import eu.aston.user.UserContext;
import eu.aston.user.UserException;
import eu.aston.utils.BaseValid;
import eu.aston.utils.ID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ognl.Ognl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller("/flow")
@SecurityRequirement(name = "BasicAuth")
@SecurityRequirement(name = "BearerAuth")
@ApiResponse(responseCode = "200", description = "ok")
@ApiResponse(responseCode = "401", description = "authorization required")
@ApiResponse(responseCode = "403", description = "forbidden")
public class FlowCaseController {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowCaseController.class);

    private final FlowCaseManager flowCaseManager;
    private final FlowDefStore flowDefStore;
    private final WaitingFlowCaseManager waitingFlowCaseManager;
    private final BlobStore blobStore;
    private final ObjectMapper objectMapper;

    public FlowCaseController(FlowCaseManager flowCaseManager, FlowDefStore flowDefStore,
                              WaitingFlowCaseManager waitingFlowCaseManager,
                              BlobStore blobStore,
                              ObjectMapper objectMapper) {
        this.flowCaseManager = flowCaseManager;
        this.flowDefStore = flowDefStore;
        this.waitingFlowCaseManager = waitingFlowCaseManager;
        this.blobStore = blobStore;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @Operation(tags = {"case"})
    @Post("/start/{caseType}")
    public IdValue createCaseFromParams(@PathVariable String caseType,
                              HttpRequest<Map<String, Object>> request,
                              @Parameter(hidden = true) UserContext userContext) {
        BaseValid.str(caseType, 1, 128, "caseType");
        FlowDef flowDef = flowDefStore.flowDef(caseType)
                .orElseThrow(()->new UserException("invalid case type, case="+caseType));
        flowDefStore.checkCaseAuth(flowDef.getAuth(), userContext, flowDef.getCode()+"/create");
        Map<String, Object> params = request.getBody().orElse(new HashMap<>());
        List<String> assets = null;
        if(flowDef.getParamsAssetExpr()!=null){
            try{
                Object val = Ognl.getValue(flowDef.getParamsAssetExpr(), params);
                if(val instanceof List<?> l)
                    assets = (List<String>) l.stream().filter(s -> s instanceof String).toList();
                else if(val instanceof String s){
                    assets = List.of(s);
                }
            }catch (Exception e){
                throw new UserException("error read assets from params, case="+caseType);
            }
        }
        String externalId = null;
        if(flowDef.getParamsExternalIdExpr()!=null){
            try{
                Object val = Ognl.getValue(flowDef.getParamsExternalIdExpr(), params);
                if(val instanceof String s){
                    externalId = s;
                } else if(val instanceof Number n){
                    externalId = n.toString();
                }
            }catch (Exception e){
                throw new UserException("error read externalId from params, case="+caseType);
            }
            BaseValid.str(externalId, -1, 128, "externalId");
        }
        Callback callback = HeaderConverter.createCallback(request.getHeaders());
        FlowCaseCreate create = new FlowCaseCreate(caseType, externalId, assets, params, callback);
        String caseId = ID.newId();
        flowCaseManager.createFlow(caseId, create);
        return new IdValue(caseId);
    }

    @Operation(tags = {"case"})
    @Post("/case")
    public IdValue createCase(@Body FlowCaseCreate caseCreate,
                              @Parameter(hidden = true) UserContext userContext) {
        BaseValid.str(caseCreate.caseType(), 1, 128, "type");
        BaseValid.str(caseCreate.externalId(), -1, 128, "externalId");

        FlowDef flowDef = flowDefStore.flowDef(caseCreate.caseType())
                .orElseThrow(()->new UserException("invalid case type, case="+caseCreate.caseType()));
        flowDefStore.checkCaseAuth(flowDef.getAuth(), userContext, flowDef.getCode()+"/create");
        String caseId = ID.newId();
        flowCaseManager.createFlow(caseId, caseCreate);
        return new IdValue(caseId);
    }

    @Operation(tags = {"case"})
    @Get("/case/{id}")
    public CompletableFuture<FlowCase> fetchCase(@PathVariable String id,
                                             @QueryValue @Nullable Integer waitTimeSeconds,
                                             @QueryValue @Nullable Boolean full,
                                             @Parameter(hidden = true) UserContext userContext){

        CompletableFuture<FlowCase> future = new CompletableFuture<>();
        FlowCase flowCase = flowCaseManager.loadFlow(id);
        if(flowCase==null){
            throw new UserException("case not found, case="+id);
        }
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                .orElseThrow(()->new UserException("invalid case type, case="+flowCase.getCaseType()+"/"+flowCase.getId()));
        flowDefStore.checkCaseAuth(flowDef.getAuth(), userContext, flowDef.getCode()+"/"+id);

        if (waitTimeSeconds!=null && waitTimeSeconds>0 && flowCase.getFinished()==null) {
            if(waitTimeSeconds>55) waitTimeSeconds = 55;
            waitingFlowCaseManager.waitFinish(flowCase, future, waitTimeSeconds);
        } else {
            future.complete(flowCase);
        }
        if(full!=null && full){
            future = future.thenApply(this::loadFullFlow);
        }
        future = future.thenApply(this::createAssetsUrl);
        return future;
    }

    private FlowCase loadFullFlow(FlowCase flowCase) {
        if(flowCase!=null && flowCase.getFinished()!=null && flowCase.getTasks()==null) {
            try{
                FlowCase finalCase = blobStore.loadFinalCase(flowCase.getCaseType(), flowCase.getId());
                if(finalCase!=null) return finalCase;
            }catch (Exception e){
                LOGGER.debug("error load final case {}",e.getMessage(), e);
                return flowCase;
            }
        }
        return flowCase;
    }

    private FlowCase createAssetsUrl(FlowCase flowCase) {
        if(flowCase!=null && flowCase.getAssets()!=null){
            for(FlowAsset asset : flowCase.getAssets()){
                try{
                    asset.setUrl(blobStore.createAssetUrl(flowCase.getCaseType(), asset.getId()));
                }catch (Exception e){
                    LOGGER.debug("error createAssetsUrl {} {}", asset.getId(), e.getMessage(), e);
                }
            }
        }
        return flowCase;
    }

    @Operation(tags = {"case"})
    @Post("/case/{id}/reprocess")
    public List<String> reprocess(@PathVariable String id,
                          @Body ClearCase clearCase,
                          @Parameter(hidden = true) UserContext userContext) {
        FlowCase flowCase = flowCaseManager.loadFlow(id);
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid case type, case="+flowCase.getCaseType()+"/"+flowCase.getId()));
        flowDefStore.checkCaseAuth(flowDef.getAuth(), userContext, flowDef.getCode()+"/"+id);
        FlowCase flowCase2 = loadFullFlow(flowCase);
        List<String> removeTasks = new ArrayList<>();
        if(flowCase2!=null && flowCase2.getTasks()!=null && !flowCase2.getTasks().isEmpty()){
            List<int[]> codeRanges = codeRanges(clearCase.responseCodes());
            removeTasks = flowCase2.getTasks().stream()
                                                .filter(t->filterClean(t,clearCase, codeRanges))
                                                .map(FlowTask::getId)
                                                .toList();
            flowCaseManager.reprocess(flowCase2.getId(), removeTasks);
        }
        return removeTasks;
    }

    private boolean filterClean(FlowTask t, ClearCase clearCase, List<int[]> codeRanges) {
        if(clearCase.steps()!=null && clearCase.steps().contains(t.getStep())) return true;
        if(clearCase.workers()!=null && (
                clearCase.workers().contains(t.getWorker())
                        || clearCase.workers().contains(t.getStep()+"."+t.getWorker())
        )) return true;
        if(clearCase.tasks()!=null && clearCase.tasks().contains(t.getId())) return true;
        if(codeRanges!=null && t.getResponseCode()>0){
            for(int[] r : codeRanges){
                if(r[0]<=t.getResponseCode() && r[1]>=t.getResponseCode()) return true;
            }
        }
        return false;
    }

    private List<int[]> codeRanges(List<String> ranges){
        if(ranges==null || ranges.isEmpty()) return null;
        List<int[]> codeRanges = new ArrayList<>();
        Pattern p = Pattern.compile("^([0-9]+)$|^([0-9]*)-([0-9]*)$");
        for (String s : ranges) {
            Matcher m = p.matcher(s);
            if(m.matches()){
                if(m.group(1)!=null){
                    int v = Integer.parseInt(m.group(1));
                    codeRanges.add(new int[]{v,v});
                } else {
                    int v0 = m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2));
                    int v1 = m.group(3).isEmpty() ? 1000 : Integer.parseInt(m.group(3));
                    codeRanges.add(new int[]{v0,v1});
                }
            }
        }
        return !codeRanges.isEmpty() ? codeRanges : null;
    }

    @Operation(tags = {"case"})
    @Post(uri = "/asset/{caseType}", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.APPLICATION_JSON)
    public IdValue createAsset(
            @Nullable @PathVariable String caseType,
            @NonNull @Part CompletedFileUpload file,
            @Nullable @Part String externalId,
            @Nullable @Part CompletedFileUpload params,
            @Nullable String callbackUrl,
            @Nullable @Part CompletedPart calbackHeaders,
            @Parameter(hidden = true) UserContext userContext) {

        String fileName = file.getFilename();
        if (fileName != null && !fileName.matches("^[A-Za-z0-9-_,\\s]{1,60}[.]{1}[A-Za-z0-9]{3,4}$"))
            fileName = null;

        FlowDef flowDef = flowDefStore.flowDef(caseType)
                .orElseThrow(()->new UserException("invalid case type, case="+caseType));
        flowDefStore.checkCaseAuth(flowDef.getAuth(), userContext, flowDef.getCode()+"/create");

        String assetId = ID.newId();
        try{
            blobStore.saveAsset(flowDef.getCode(), assetId, file.getBytes(), fileName);
        }catch (Exception e){
            LOGGER.debug("error saveAsset {} {}", assetId, e.getMessage(), e);
            throw new UserException("save asset error "+e.getMessage());
        }

        if(externalId!=null){
            BaseValid.str(externalId, -1, 128, "externalId");
            Map<String, Object> parsedParams = null;
            if(params!=null){
                try{
                    MapType type = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
                    parsedParams = objectMapper.readValue(params.getInputStream(), type);
                }catch (Exception e){
                    throw new UserException("params not cast to json object");
                }
            }

            Callback callback = null;
            if(callbackUrl!=null){
                Map<String, String> headers = new HashMap<>();
                if(calbackHeaders!=null){
                    try{
                        MapType type = objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class);
                        headers = objectMapper.readValue(calbackHeaders.getBytes(), type);
                    }catch (Exception e){
                        throw new UserException("callback headers not cast to map");
                    }
                }
                callback = new Callback(callbackUrl, headers);
            }

            FlowCaseCreate create = new FlowCaseCreate(
                    caseType,
                    externalId,
                    List.of(assetId),
                    parsedParams,
                    callback);
            flowCaseManager.createFlow(assetId, create);
        }

        return new IdValue(assetId);
    }
}
