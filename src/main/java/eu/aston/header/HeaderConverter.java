package eu.aston.header;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import eu.aston.flow.task.TaskHttpRequest;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpHeaders;

public class HeaderConverter {

    public static final String H_CASE_ID = "fw-case-id";
    public static final String H_ID = "fw-event-id";
    public static final String H_METHOD = "fw-method";
    public static final String H_URI = "fw-uri";
    public static final String H_STATUS = "fw-status";
    public static final String H_CALLBACK_URL = "fw-callback";
    public static final String H_CALLBACK_PREFIX = "fw-callback-";
    public static final String H_HEADER = "fw-header-";
    public static final Set<String> headerNamesRequest = Set.of("authorization", "x-api-key", "content-type", "content-encoding");
    public static final Set<String> headerNamesResponse = Set.of("content-type", "content-encoding", "e-tag");

    public static Map<String, String> eventRequest(HttpHeaders headers, String id, String method, String path) {
        Map<String, String> map = new HashMap<>();
        headers.forEach((k,v)->{
            if(k.startsWith(H_HEADER)){
                map.put(k, v.getFirst());
            } else if(headerNamesRequest.contains(k)){
                map.put(H_HEADER +k, v.getFirst());
            }
        });
        map.put(H_ID, id);
        map.put(H_METHOD, method);
        map.put(H_URI, path);
        return map;
    }

    public static Map<String, String> eventResponse(@NonNull HttpHeaders headers, String id) {
        Map<String, String> map = new HashMap<>();
        headers.forEach((k,v)->{
            if(k.startsWith(H_HEADER)){
                map.put(k.substring(H_HEADER.length()), v.getFirst());
            } else if(headerNamesResponse.contains(k)){
                map.put(k, v.getFirst());
            }
        });
        map.put(H_ID, id);
        return map;
    }

    public static Callback createCallback(HttpHeaders headers){
        Callback callback = null;
        String callbackUrl = headers.get(H_CALLBACK_URL);
        if(callbackUrl!=null){
            Map<String, String> callbackHeaders = new HashMap<>();
            headers.forEach((k, v)->{
                if(k.startsWith(H_CALLBACK_PREFIX)){
                    callbackHeaders.put(k.substring(H_CALLBACK_PREFIX.length()), v.getFirst());
                }
            });
            callback = new Callback(callbackUrl, callbackHeaders);
        }
        return callback;
    }

    public static Map<String, String> queueRequest(TaskHttpRequest request) {
        Map<String, String> map = new HashMap<>();
        if(request.headers()!=null){
            request.headers().forEach((k,v)->{
                if(!headerNamesRequest.contains(k) && !k.startsWith("fw-")){
                    map.put(H_HEADER+k, v);
                } else {
                    map.put(k, v);
                }
            });
        }
        return map;
    }
}
