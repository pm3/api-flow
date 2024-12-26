package eu.aston.flow.store;

import java.util.Map;

import eu.aston.micronaut.sql.entity.Table;

@Table(name = "flow_request")
public class FlowRequestEntity {
    private String id;
    private String flowCaseId;
    private String method;
    private String path;
    private String body;

    public FlowRequestEntity(){
    }

    public FlowRequestEntity(String id, String flowCaseId, String method, String path, String body) {
        this.id = id;
        this.flowCaseId = flowCaseId;
        this.method = method;
        this.path = path;
        this.body = body;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFlowCaseId() {
        return flowCaseId;
    }

    public void setFlowCaseId(String flowCaseId) {
        this.flowCaseId = flowCaseId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
