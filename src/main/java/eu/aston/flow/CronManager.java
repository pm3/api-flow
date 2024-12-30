package eu.aston.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.aston.flow.def.CronJob;
import eu.aston.flow.model.FlowCaseCreate;
import eu.aston.utils.CronPattern;
import eu.aston.utils.ID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CronManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CronManager.class);
    private final FlowCaseManager flowCaseManager;
    private final List<FlowCron> flowCronItems;

    public CronManager(FlowDefStore flowDefStore, FlowCaseManager flowCaseManager) {
        this.flowCaseManager = flowCaseManager;
        this.flowCronItems = createFlowCronItems(flowDefStore.listFlows());
    }

    public boolean hasCronJobs() {
        return !flowCronItems.isEmpty();
    }

    private List<FlowCron> createFlowCronItems(List<IFlowDef> flowDefs) {
        List<FlowCron> flowCronItems = new ArrayList<>();
        for (IFlowDef flowDef : flowDefs) {
            if(flowDef.getCronJobs()!=null){
                for(CronJob cronJob : flowDef.getCronJobs()){
                    try{
                        flowCronItems.add(new FlowCron(new CronPattern(cronJob.expression()), flowDef, cronJob.params()));
                    }catch (Exception e){
                        LOGGER.warn("ignore flow cron job {}/{} in flow - {}", flowDef.getCode(), cronJob.expression(), e.getMessage());
                    }
                }
            }
        }
        return flowCronItems;
    }

    public void runNow() {
        CronPattern.matchAll(flowCronItems, FlowCron::pattern).forEach(this::run);
    }

    public void run(FlowCron flowCron){
        LOGGER.info("run flow cron job {}/{}", flowCron.flowDef.getCode(), flowCron.pattern);
        String caseId = ID.newId();
        FlowCaseCreate caseCreate = new FlowCaseCreate(flowCron.flowDef.getCode(), null, null, flowCron.params(), null);
        flowCaseManager.createFlow(caseId, caseCreate);
    }

    public record FlowCron(CronPattern pattern, IFlowDef flowDef, Map<String, Object> params) {
    }
}
