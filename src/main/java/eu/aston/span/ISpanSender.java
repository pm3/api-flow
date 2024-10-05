package eu.aston.span;

import eu.aston.flow.def.FlowDef;
import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;

public interface ISpanSender {

    void createFlow(FlowCaseEntity flowCase);

    void finishFlow(FlowCaseEntity flowCase, FlowDef flowDef, String error);

    void finishTask(FlowCaseEntity flowCase, FlowTaskEntity task, FlowWorkerDef workerDef);
}
