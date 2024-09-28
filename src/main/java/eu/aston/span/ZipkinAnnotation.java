package eu.aston.span;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ZipkinAnnotation(long timestamp, String value){}
