package eu.aston.span;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ZipkinEndpoint(String serviceName){}

