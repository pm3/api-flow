package eu.aston.flow;

import java.util.Map;

public interface IFlowBridge {
    boolean sendQueueEvent(String caseId, String taskId, String method,String path, Map<String, String> headers, byte[] body);
}
