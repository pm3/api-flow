package eu.aston.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import eu.aston.header.CallbackRunner;
import eu.aston.utils.SuperTimer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueStore.class);
    private final ConcurrentHashMap<String, QueueEvent> eventMap = new ConcurrentHashMap<>();
    private final TreeMap<String, WorkerGroup> workerTree = new TreeMap<>();
    private final SuperTimer superTimer;
    private final IQueueBridge queueBridge;
    private final CallbackRunner callbackRunner;

    public QueueStore(SuperTimer superTimer, IQueueBridge queueBridge, CallbackRunner callbackRunner) {
        this.superTimer = superTimer;
        this.queueBridge = queueBridge;
        this.callbackRunner = callbackRunner;
        superTimer.schedulePeriod(Duration.ofMinutes(10).toMillis(), this::cleanSentEventMap);
    }

    public WorkerGroup workerGroupByPath(String path) {
        synchronized (workerTree) {
            Map.Entry<String, WorkerGroup> entry = workerTree.floorEntry(path);
            if (entry != null && path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void addEvent(QueueEvent event, int timeout) {
        WorkerGroup workerGroup = workerGroupByPath(event.getPath());
        addEvent(workerGroup, event, timeout);
    }

    public void addEvent(WorkerGroup workerGroup, QueueEvent event, int timeout) {
        LOGGER.debug("addEvent {} {} => {}", event.getId(), event.getPath(), workerGroup!=null ? workerGroup.prefix : null);
        eventMap.put(event.getId(), event);
        if (timeout > 0 && event.getWaitingWriter()!=null) {
            superTimer.schedule(timeout * 1000L, event.getId(), this::responseTimeout);
        }
        if (workerGroup != null) {
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w));
            if (!sent) {
                LOGGER.debug("waiting in queue {}", event.getId());
                workerGroup.events.add(event);
            }
        } else {
            superTimer.schedule(120 * 1000L, event.getId(), this::response503);
        }
    }

    private void response503(String requestId){
        response(requestId, 503, null, "<h1>Service Unavailable</h1>".getBytes(StandardCharsets.UTF_8));
    }

    private void responseTimeout(String requestId) {
        QueueEvent event = eventMap.remove(requestId);
        var w = event.clearWaitingWriter();
        if (w != null) {
            event.setT3(504);
            w.complete(HttpResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                                   .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                                   .body("<h1>Gateway Timeout</h1>"));
        }
    }

    private boolean nextWorker(WorkerGroup workerGroup, Consumer<CompletableFuture<HttpResponse<?>>> sender) {
        while (true) {
            Worker worker = null;
            try {
                worker = workerGroup.workers.poll(25, TimeUnit.MICROSECONDS);
            }catch (InterruptedException ignore){}
            if(worker==null) break;
            var w = worker.removeResponse();
            if (w != null) {
                try {
                    sender.accept(w);
                    return true;
                } catch (Exception e) {
                    LOGGER.debug("error write worker");
                }
            }
        }
        return false;
    }

    private void sendRemoteEvent(QueueEvent event, CompletableFuture<HttpResponse<?>> w) {
        event.setT2(System.currentTimeMillis());
        w.complete(HttpResponse.ok((Object) event.getBody()).headers(new HashMap<>(event.getHeaders())));
        if(queueBridge!=null) {
            queueBridge.queueEventSent(event.getId());
        }
    }

    public void workerQueue(Worker worker) {
        String prefix = worker.getPrefix();
        WorkerGroup workerGroup;
        synchronized (workerTree) {
            workerGroup = workerTree.get(prefix);
            if(workerGroup==null){
                workerGroup = new WorkerGroup(prefix);
                workerTree.put(prefix, workerGroup);
                LOGGER.info("create new worker group {}", prefix);
                workerGroupAddEvents(workerGroup, new ArrayList<>(eventMap.values().stream().filter(e->e.getPath().startsWith(prefix)).toList()));
            }
        }
        workerGroup.lastWorker = System.currentTimeMillis();
        QueueEvent event = workerGroup.events.poll();
        if (event != null) {
            sendRemoteEvent(event, worker.removeResponse());
        } else {
            workerGroup.workers.add(worker);
            superTimer.schedule(worker.getTimeout() * 1000L, worker, this::timeoutWorker);
        }
    }

    private void workerGroupAddEvents(WorkerGroup workerGroup, List<QueueEvent> list) {
        list.sort((e1,e2)->(int)(e1.getT1()-e2.getT1()));
        for (QueueEvent event : list){
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w));
            if (!sent) {
                workerGroup.events.add(event);
            }
        }
    }

    private void timeoutWorker(Worker worker) {
        var writer = worker.removeResponse();
        if (writer != null) {
            try{
                writer.complete(HttpResponse.accepted());
                LOGGER.debug("worker timeout {}", worker.getPrefix());
            }catch (Exception ignore){}
        }
    }

    public void response(String eventId, int status, Map<String, String> headers, byte[] body) {
        LOGGER.debug("event response {} {} {}", eventId, status, new String(body));
        QueueEvent event = eventMap.remove(eventId);
        if (event != null) {
            event.setT3(status);
            var w = event.clearWaitingWriter();
            if (w != null) {
                w.complete(HttpResponse.ok((Object) body).headers(new HashMap<>(headers)));
                LOGGER.debug("event response {}", event);
            } else if (event.getCallback()!=null) {
                if(event.getCallback().headers()!=null) headers.putAll(event.getCallback().headers());
                callbackRunner.callbackAsync(eventId, event.getCallback(), headers, body);
            } else if(queueBridge!=null && !queueBridge.eventResponse(event, status, headers, body)){
                LOGGER.debug("event without callback {}", eventId);
            }
        }
    }

    private void cleanSentEventMap() {
        List<QueueEvent> list = new ArrayList<>(eventMap.values());
        long expired = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis();
        for (QueueEvent event : list) {
            if (event.getT2() < expired) {
                eventMap.remove(event.getId());
                if (event.getT3() == 0) {
                    event.setT3(-1);
                }
            }
        }
    }

}
