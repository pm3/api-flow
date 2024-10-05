package eu.aston;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.blob.AzureBlobAuthBuilder;
import eu.aston.blob.BlobStore;
import eu.aston.flow.FlowDefStore;
import eu.aston.flow.nodejs.NodeJsFlowExecutor;
import eu.aston.utils.JwtVerify;
import eu.aston.utils.SuperTimer;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Factory
public class AppFactory {

    @Singleton
    public SuperTimer superTimer(){
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        return new SuperTimer(executor);
    }

    @Singleton
    public HttpClient httpClient(){
        return HttpClient.newBuilder()
                         .version(HttpClient.Version.HTTP_1_1)
                         .connectTimeout(Duration.ofSeconds(6))
                         .build();
    }

    @Singleton
    public BlobStore blobStore(HttpClient httpClient,
                               ObjectMapper objectMapper,
                               @Value("${blob.url}") String blobUrl,
                               @Value("${blob.auth}") String blobAuth){
        AzureBlobAuthBuilder azureBlobAuthBuilder = new AzureBlobAuthBuilder(blobUrl, blobAuth);
        return new BlobStore(httpClient, objectMapper, azureBlobAuthBuilder);
    }

    @Singleton
    public JwtVerify jwtVerify(HttpClient httpClient, ObjectMapper objectMapper){
        return new JwtVerify(httpClient, objectMapper);
    }

    @Singleton
    public FlowDefStore flowDefStore(HttpClient httpClient,
                                     ObjectMapper objectMapper,
                                     @Value("${app.rootDir}") File root,
                                     JwtVerify jwtVerify,
                                     NodeJsFlowExecutor nodeJsFlowExecutor){
        FlowDefStore flowDefStore = new FlowDefStore(httpClient, objectMapper, jwtVerify, nodeJsFlowExecutor);
        flowDefStore.setDefaultTimeout(120);
        flowDefStore.loadRoot(root, false);
        return flowDefStore;
    }
}
