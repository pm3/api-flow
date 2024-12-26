package eu.aston.flow;

import java.util.List;
import java.util.Map;

import eu.aston.flow.def.JwtIssuerDef;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;

public interface IFlowDef {
    String getCode();

    Map<String, String> getLabels();

    List<String> getAuthApiKeys();

    List<JwtIssuerDef> getAuthJwtIssuers();

    List<TaskHttpRequest> execTick(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks);
}