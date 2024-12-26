package eu.aston.flow.model;

import java.time.Instant;

import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class FlowTask {

    public static final String STEP_ITERATOR = "_iterator";
    public static final String FLOW_RESPONSE = "_response";
    public static final String ECHO = "echo";

    private String id;
    private String step;
    private String worker;
    private int stepIndex;

    private int responseCode;
    @Format(JsonConverterFactory.JSON)
    private Object response;
    private String error;

    private Instant created;
    private Instant finished;

    private FlowRequest request;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
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

    public Instant getFinished() {
        return finished;
    }

    public void setFinished(Instant finished) {
        this.finished = finished;
    }

    public FlowRequest getRequest() {
        return request;
    }

    public void setRequest(FlowRequest request) {
        this.request = request;
    }

    @Override
    public String toString() {
        return "FlowTask{" + "id='" + id + '\'' + ", step='" + step + '\'' + ", worker='" + worker + '\'' +
                ", stepIndex='" + stepIndex + '\'' + '}';
    }
}
