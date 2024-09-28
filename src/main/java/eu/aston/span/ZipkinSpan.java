package eu.aston.span;

import java.util.List;
import java.util.Map;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class ZipkinSpan {
    private String traceId;
    private String name;
    private String id;
    private String parentId;
    private long timestamp;
    private long duration;
    private ZipkinEndpoint localEndpoint;
    private Map<String, String> tags;
    private List<ZipkinAnnotation> annotations;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public ZipkinEndpoint getLocalEndpoint() {
        return localEndpoint;
    }

    public void setLocalEndpoint(ZipkinEndpoint localEndpoint) {
        this.localEndpoint = localEndpoint;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public List<ZipkinAnnotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<ZipkinAnnotation> annotations) {
        this.annotations = annotations;
    }
}
