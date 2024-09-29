package eu.aston.flow.def;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FlowWorkerDef {
    private String code;
    private String path;
    @JsonProperty(value = "$path")
    private String pathExpr;
    private String method;
    private Map<String, String> headers;
    private Map<String, Object> params;
    private String where;
    private Map<String, String> labels;
    private boolean blocked;
    private Integer timeout;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPathExpr() {
        return pathExpr;
    }

    public void setPathExpr(String pathExpr) {
        this.pathExpr = pathExpr;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return "FlowWorkerDef{" + "code='" + code + '\'' + ", path='" + path + '\'' + '}';
    }
}
