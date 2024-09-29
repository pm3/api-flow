package eu.aston;

import java.io.File;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import eu.aston.blob.AzureBlobAuthBuilder;
import eu.aston.blob.BlobStore;
import eu.aston.flow.FlowCaseManager;
import eu.aston.flow.FlowDefStore;
import eu.aston.flow.IFlowExecutor;
import eu.aston.flow.WaitingFlowCaseManager;
import eu.aston.flow.nodejs.NodeJsFlowExecutor;
import eu.aston.flow.store.IFlowCaseStore;
import eu.aston.flow.store.IFlowTaskStore;
import eu.aston.header.CallbackRunner;
import eu.aston.queue.QueueStore;
import eu.aston.span.ISpanSender;
import eu.aston.utils.JwtVerify;
import eu.aston.utils.SuperTimer;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Singleton
    public FlowCaseManager flowCaseManager(BlobStore blobStore,
                                           IFlowCaseStore caseStore,
                                           IFlowTaskStore taskStore,
                                           FlowDefStore flowDefStore,
                                           IFlowExecutor[] executors,
                                           WaitingFlowCaseManager waitingFlowCaseManager,
                                           ISpanSender spanSender,
                                           QueueFlowBridge queueFlowBridge,
                                           CallbackRunner callbackRunner) {
        FlowCaseManager flowCaseManager = new FlowCaseManager(blobStore,caseStore,taskStore,flowDefStore,
                                                              executors, waitingFlowCaseManager, spanSender,
                                                              callbackRunner);
        queueFlowBridge.setFlowCaseManager(flowCaseManager);
        return flowCaseManager;
    }

    @Singleton
    public QueueStore queueStore(HttpClient httpClient,
                                 SuperTimer superTimer,
                                 QueueFlowBridge queueFlowBridge,
                                 CallbackRunner callbackRunner) {
        QueueStore queueStore = new QueueStore(superTimer, queueFlowBridge, callbackRunner);
        queueFlowBridge.setQueueStore(queueStore);
        return queueStore;
    }

}
