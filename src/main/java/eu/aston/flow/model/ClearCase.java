package eu.aston.flow.model;

import java.util.List;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ClearCase(List<String> steps,
                        List<String> workers,
                        List<String> tasks,
                        List<String> responseCodes) {}
