package eu.aston.controller;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.AppConfig;
import eu.aston.flow.FlowCaseManager;
import eu.aston.header.HeaderConverter;
import eu.aston.user.UserException;
import eu.aston.utils.Hash;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Controller("/flow")
@SecurityRequirement(name = "ApiKeyAuth")
@ApiResponse(responseCode = "200", description = "ok")
@ApiResponse(responseCode = "401", description = "authorization required")
public class CallbackController {

    private final FlowCaseManager flowCaseManager;
    private final ObjectMapper objectMapper;
    private final byte[] taskApiKeySecret;

    public CallbackController(FlowCaseManager flowCaseManager, ObjectMapper objectMapper, AppConfig appConfig) {
        this.flowCaseManager = flowCaseManager;
        this.objectMapper = objectMapper;
        this.taskApiKeySecret = appConfig.getTaskApiKeySecret().getBytes(StandardCharsets.UTF_8);
    }

    @Operation(tags = {"internal"})
    @Post(uri = "/response/{taskId}", consumes = MediaType.APPLICATION_JSON)
    public void response(@PathVariable String taskId,
                         @Nullable @Header(HeaderConverter.H_STATUS) Integer callbackStatus,
                         @Nullable @Header("X-Api-Key") String requestApiKey,
                         @Body byte[] data) {

        String apiKey = Hash.hmacSha1(taskId.getBytes(StandardCharsets.UTF_8), taskApiKeySecret);
        if(!Objects.equals(apiKey, requestApiKey)){
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "invalid X-Api-Key");
        }

        if(callbackStatus==null) callbackStatus = 200;
        try {
            if(callbackStatus>=200 && callbackStatus<300) {
                Object root = objectMapper.readValue(data, Object.class);
                flowCaseManager.finishTask(taskId, callbackStatus, root);
            } else {
                flowCaseManager.finishTask(taskId, callbackStatus, new String(data, StandardCharsets.UTF_8));
            }
        }catch (Exception e){
            flowCaseManager.finishTask(taskId, 400, "parse json body error "+e.getMessage());
            throw new UserException("body not parse to json");
        }
    }

}
