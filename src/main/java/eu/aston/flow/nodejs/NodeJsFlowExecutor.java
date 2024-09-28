package eu.aston.flow.nodejs;

import java.io.File;
import java.util.List;

import eu.aston.flow.IFlowExecutor;
import eu.aston.flow.def.FlowDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import jakarta.inject.Singleton;

@Singleton
public class NodeJsFlowExecutor implements IFlowExecutor {

    public static final String ID = "node";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks,
                         String stepCode, IFlowBack flowBack) {

    }

    public FlowDef initJsFlowDef(File fileJs){
        return null;
    }
}
