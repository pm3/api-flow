package eu.aston.flow.ognl;

import java.util.List;
import java.util.Map;

import eu.aston.flow.def.CronJob;
import eu.aston.flow.def.JwtIssuerDef;
import io.micronaut.core.annotation.Introspected;

@Introspected
public record OgnlFlowData(List<WorkerDef> workers,
                           Map<String, Object> response,
                           Map<String, String> labels,
                           List<String> authApiKeys,
                           List<JwtIssuerDef> authJwtIssuers,
                           List<CronJob> cronJobs) {
}
