package eu.aston.flow.ognl;

import java.util.List;
import java.util.Map;

import eu.aston.flow.def.FlowWorkerDef;
import eu.aston.flow.def.JwtIssuerDef;
import io.micronaut.core.annotation.Introspected;

@Introspected
public record OgnlFlowData(List<FlowWorkerDef> workers,
                           Map<String, Object> response,
                           Map<String, String> labels,
                           List<String> authApiKeys,
                           List<JwtIssuerDef> authJwtIssuers) {
}
