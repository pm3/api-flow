package eu.aston.flow.def;

import java.util.List;

public class FlowStepDef {
    private String code;
    private String itemsExpr;
    private List<FlowWorkerDef> workers;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getItemsExpr() {
        return itemsExpr;
    }

    public void setItemsExpr(String itemsExpr) {
        this.itemsExpr = itemsExpr;
    }

    public List<FlowWorkerDef> getWorkers() {
        return workers;
    }

    public void setWorkers(List<FlowWorkerDef> workers) {
        this.workers = workers;
    }
}
