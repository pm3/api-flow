package eu.aston.flow;

import java.util.List;

import eu.aston.flow.def.FlowDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;

public interface IFlowExecutor {

    String id();

    List<TaskHttpRequest> execTick(FlowDef flowDef, FlowCaseEntity flowCase, List<FlowTaskEntity> tasks, String stepCode);
}
