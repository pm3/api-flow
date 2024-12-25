package eu.aston.flow.nodejs;

import java.util.List;

import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import io.micronaut.core.annotation.Introspected;

@Introspected
public record NodeJsFlowRequest(FlowCaseEntity flowCase, List<FlowTaskEntity> tasks) {
}
