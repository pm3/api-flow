package eu.aston.flow.task;

import java.util.Map;

public record TaskHttpRequest(
        String taskId,
        String method,
        String path,
        Map<String, String> headers,
        String body,
        boolean blocked,
        String error
){
    public static TaskHttpRequest of(String taskId, String error){
        return new TaskHttpRequest(taskId, null, null, null, null, false, error);
    }
}
