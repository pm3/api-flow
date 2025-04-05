package eu.aston.queue;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerGroup {
    public final String prefix;
    public final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    public final BlockingQueue<Worker> workers = new LinkedBlockingQueue<>();
    public long lastWorker = 0L;
    public Map<String, Long> lastWorkerPing = new ConcurrentHashMap<>();
    public AtomicInteger eventCounter = new AtomicInteger();

    public long lastWorkerFast = 0L;
    public final Queue<Worker> slowWorkers = new ConcurrentLinkedQueue<>();

    public WorkerGroup(String prefix) {
        this.prefix = prefix;
    }
}
