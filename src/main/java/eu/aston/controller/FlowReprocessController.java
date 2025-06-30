package eu.aston.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.aston.blob.BlobStore;
import eu.aston.flow.FlowCaseManager;
import eu.aston.flow.FlowDefStore;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.model.ClearCase;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.FlowTask;
import eu.aston.flow.model.IdValue;
import eu.aston.user.UserException;
import eu.aston.utils.ID;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller("/flow")
@SecurityRequirement(name = "BasicAuth")
@SecurityRequirement(name = "BearerAuth")
@ApiResponse(responseCode = "200", description = "ok")
@ApiResponse(responseCode = "401", description = "authorization required")
@ApiResponse(responseCode = "403", description = "forbidden")
public class FlowReprocessController {

    private final static Logger LOGGER = LoggerFactory.getLogger(FlowReprocessController.class);

    private final FlowCaseManager flowCaseManager;
    private final FlowDefStore flowDefStore;
    private final BlobStore blobStore;

    public FlowReprocessController(FlowCaseManager flowCaseManager, FlowDefStore flowDefStore, BlobStore blobStore) {
        this.flowCaseManager = flowCaseManager;
        this.flowDefStore = flowDefStore;
        this.blobStore = blobStore;
    }

    @Operation(tags = {"case"})
    @Post("/case/{id}/reprocess")
    public IdValue reprocess(@PathVariable String id,
                             @Body ClearCase clearCase,
                             @Nullable @Header("X-Api-Key") String apiKey) {
        FlowCase flowCase = flowCaseManager.loadFlow(id);
        if(flowCase==null){
            throw new UserException("case not found, case="+id);
        }
        if(flowCase.getFinished()==null){
            throw new UserException("case not finished, case="+id);
        }
        FlowDef flowDef = flowDefStore.flowDef(flowCase.getCaseType())
                                      .orElseThrow(()->new UserException("invalid case type, case="+flowCase.getCaseType()+"/"+flowCase.getId()));
        flowDefStore.checkCaseAuth(flowDef, apiKey);

        FlowCase finalCase = null;
        try{
            finalCase = blobStore.loadFinalCase(flowCase.getCaseType(), flowCase.getId());
        }catch (Exception e){
            LOGGER.debug("error load final case {}",e.getMessage(), e);
        }
        if(finalCase==null || finalCase.getTasks()==null){
            throw new UserException("final case not found, case="+id);
        }
        String newCaseId = ID.newId();
        LOGGER.info("reprocess flow {}/{} => {}", flowCase.getCaseType(), id, newCaseId);
        List<int[]> codeRanges = codeRanges(clearCase.responseCodes());
        List<FlowTask> copyTasks = finalCase.getTasks().stream()
                                            .filter(t->!filterClean(t,clearCase, codeRanges))
                                            .toList();
        flowCaseManager.reprocess(newCaseId, finalCase, copyTasks);
        return new IdValue(newCaseId);
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
}
