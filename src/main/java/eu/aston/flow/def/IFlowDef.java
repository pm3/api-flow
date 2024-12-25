package eu.aston.flow.def;

import java.util.List;
import java.util.Map;

import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;

public interface IFlowDef {
    String getCode();

    Map<String, String> getLabels();

    List<String> getAuthApiKeys();

    List<JwtIssuerDef> getAuthJwtIssuers();

    FlowWorkerDef worker(String name);

    List<TaskHttpRequest> execTick(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks);
}