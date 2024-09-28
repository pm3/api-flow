package eu.aston;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("app")
public class AppConfig {
    private String appHost;
    private String taskApiKeySecret;
    private String workerApiKey;

    public String getAppHost() {
        return appHost;
    }

    public void setAppHost(String appHost) {
        this.appHost = appHost;
    }

    public String getTaskApiKeySecret() {
        return taskApiKeySecret;
    }

    public void setTaskApiKeySecret(String taskApiKeySecret) {
        this.taskApiKeySecret = taskApiKeySecret;
    }

    public String getWorkerApiKey() {
        return workerApiKey;
    }

    public void setWorkerApiKey(String workerApiKey) {
        this.workerApiKey = workerApiKey;
    }
}
