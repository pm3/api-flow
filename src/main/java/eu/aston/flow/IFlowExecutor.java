package eu.aston.flow;

import java.util.List;

import eu.aston.flow.def.FlowDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;

public interface IFlowExecutor {

    String id();

    void execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks, String stepCode, IFlowBack flowBack);

    interface IFlowBack{
        void sentTask(FlowTaskEntity task);
        void finishTask(FlowTaskEntity task, int statusCode, Object response);
    }
}
