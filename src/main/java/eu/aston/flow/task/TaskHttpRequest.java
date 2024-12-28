package eu.aston.flow.task;

import java.util.Map;

public record TaskHttpRequest(
        String worker,
        String step,
        int stepIndex,
        String method,
        String path,
        Map<String, String> headers,
        String body,
        boolean blocked,
        String error
){
    public static TaskHttpRequest of(String worker,
                                     String step,
                                     int stepIndex,
                                     String method,
                                     String path,
                                     Map<String, String> headers,
                                     String body,
                                     boolean blocked){
        return new TaskHttpRequest(worker, step, stepIndex, method, path, headers, body, blocked, null);
    }
    public static TaskHttpRequest of(String worker, String step, int stepIndex, String error){
        return new TaskHttpRequest(worker, step, stepIndex, null, null, null, null, false, error);
    }
}
