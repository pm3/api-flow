package eu.aston.flow.model;

import java.util.List;
import java.util.Map;

import eu.aston.header.Callback;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

@Introspected
public record FlowCaseCreate(@NonNull String caseType,
                             @Nullable String externalId,
                             @Nullable List<String> assets,
                             @Nullable Map<String, Object> params,
                             @Nullable Callback callback) {}
