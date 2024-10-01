package eu.aston.queue;

import java.util.concurrent.LinkedBlockingQueue;

public class WorkerGroup {
    public final String prefix;
    public final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
    public final LinkedBlockingQueue<Worker> workers = new LinkedBlockingQueue<>();
    public long lastWorker = 0L;

    public WorkerGroup(String prefix) {
        this.prefix = prefix;
    }
}
