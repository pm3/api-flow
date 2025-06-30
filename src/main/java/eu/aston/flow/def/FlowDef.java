package eu.aston.flow.def;

import java.util.List;
import java.util.Map;

public final class FlowDef {
    private String code;
    private List<String> apiKeys;
    private String executor;
    private List<FlowStepDef> steps;
    private Map<String, String> labels;

    private Map<String, Object> response;
    private String paramsAssetExpr;
    private String paramsExternalIdExpr;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getExecutor() {
        return executor;
    }

    public void setExecutor(String executor) {
        this.executor = executor;
    }

    public List<FlowStepDef> getSteps() {
        return steps;
    }

    public void setSteps(List<FlowStepDef> steps) {
        this.steps = steps;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, Object> getResponse() {
        return response;
    }

    public void setResponse(Map<String, Object> response) {
        this.response = response;
    }

    public String getParamsAssetExpr() {
        return paramsAssetExpr;
    }

    public void setParamsAssetExpr(String paramsAssetExpr) {
        this.paramsAssetExpr = paramsAssetExpr;
    }

    public String getParamsExternalIdExpr() {
        return paramsExternalIdExpr;
    }

    public void setParamsExternalIdExpr(String paramsExternalIdExpr) {
        this.paramsExternalIdExpr = paramsExternalIdExpr;
    }

    @Override
    public String toString() {
        return "FlowDef{" + "code='" + code + '\'' + ", executor='" + executor + '\'' + '}';
    }
}

