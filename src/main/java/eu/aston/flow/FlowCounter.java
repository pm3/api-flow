package eu.aston.flow;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Singleton;

@Singleton
public class FlowCounter {

    private final Map<String, Integer> flowTotal = new HashMap<>();
    private final Map<String, Integer> flowOk = new HashMap<>();
    private final Map<String, Integer> flowError = new HashMap<>();

    public void incrementFlowTotal(String flow) {
        flowTotal.merge(flow, 1, Integer::sum);
    }

    public void incrementFlowOk(String flow) {
        flowOk.merge(flow, 1, Integer::sum);
    }

    public void incrementFlowError(String flow) {
        flowError.merge(flow, 1, Integer::sum);
    }

    public Map<String, Integer> getFlowTotal() {
        return flowTotal;
    }

    public Map<String, Integer> getFlowOk() {
        return flowOk;
    }

    public Map<String, Integer> getFlowError() {
        return flowError;
    }
}
