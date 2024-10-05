package eu.aston.queue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerGroup {
    public final String prefix;
    public final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
    public final LinkedBlockingQueue<Worker> workers = new LinkedBlockingQueue<>();
    public long lastWorker = 0L;
    public Map<String, Long> lastWorkerPing = new ConcurrentHashMap<>();
    public AtomicInteger eventCounter = new AtomicInteger();

    public WorkerGroup(String prefix) {
        this.prefix = prefix;
    }
}
