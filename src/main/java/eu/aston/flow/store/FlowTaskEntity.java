package eu.aston.flow.store;

import java.time.Instant;

import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;
import eu.aston.micronaut.sql.entity.Table;

@Table(name = "flow_task")
public class FlowTaskEntity {
    private String id;
    private String flowCaseId;
    private String step;
    private String worker;
    private int stepIndex;

    private Integer responseCode;
    @Format(JsonConverterFactory.JSON)
    private Object response;
    private String error;

    private Instant created;
    private Instant started;
    private Instant finished;
    private Instant queueSent;

    public FlowTaskEntity() {
    }

    public FlowTaskEntity(String id, String flowCaseId, String step, String worker, int stepIndex) {
        this.id = id;
        this.flowCaseId = flowCaseId;
        this.step = step;
        this.worker = worker;
        this.stepIndex = stepIndex;
        this.created = Instant.now();
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

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getStarted() {
        return started;
    }

    public void setStarted(Instant started) {
        this.started = started;
    }

    public Instant getFinished() {
        return finished;
    }

    public void setFinished(Instant finished) {
        this.finished = finished;
    }

    public Instant getQueueSent() {
        return queueSent;
    }

    public void setQueueSent(Instant queueSent) {
        this.queueSent = queueSent;
    }

    @Override
    public String toString() {
        return "Task "+step+"/"+worker+" case="+flowCaseId+" id="+id;
    }
}
