package eu.aston;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("app")
public class AppConfig {
    private String appHost;
    private String queueHost;
    private String taskApiKeySecret;
    private String workerApiKey;
    private int defaultTimeout;

    public String getAppHost() {
        return appHost;
    }

    public void setAppHost(String appHost) {
        this.appHost = appHost;
    }

    public String getQueueHost() {
        return queueHost;
    }

    public void setQueueHost(String queueHost) {
        this.queueHost = queueHost;
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

    public int getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
}
