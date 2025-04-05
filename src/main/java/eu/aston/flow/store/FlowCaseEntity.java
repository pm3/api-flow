package eu.aston.flow.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import eu.aston.flow.model.CaseState;
import eu.aston.flow.model.FlowAsset;
import eu.aston.header.Callback;
import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;
import eu.aston.micronaut.sql.entity.Table;

@Table(name = "flow_case")
public class FlowCaseEntity {
    private String id;
    private String caseType;
    private String externalId;
    @Format(JsonConverterFactory.JSON)
    private Callback callback;

    @Format(JsonConverterFactory.JSON)
    private Map<String, Object> params;
    @Format(JsonConverterFactory.JSON)
    private List<FlowAsset> assets;
    @Format(JsonConverterFactory.JSON)
    private Object response;

    private Instant created;
    private Instant finished;
    private CaseState state;
    private String step;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public List<FlowAsset> getAssets() {
        return assets;
    }

    public void setAssets(List<FlowAsset> assets) {
        this.assets = assets;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
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

    public CaseState getState() {
        return state;
    }

    public void setState(CaseState state) {
        this.state = state;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    @Override
    public String toString() {
        return "FlowCaseEntity{" + "id='" + id + '\'' + ", caseType='" + caseType +
                '\'' + ", externalId='" + externalId + '\'' + '}';
    }
}
