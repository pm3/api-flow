package eu.aston.header;

import java.util.Map;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

@Introspected
public record Callback(@NonNull String url,
                       @Nullable Map<String, String> headers) {}
