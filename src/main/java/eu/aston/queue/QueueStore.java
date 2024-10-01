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
import io.micronaut.http.HttpResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QueueStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueStore.class);
    private final ConcurrentHashMap<String, QueueEvent> eventMap = new ConcurrentHashMap<>();
    private final TreeMap<String, WorkerGroup> workerTree = new TreeMap<>();
    private final SuperTimer superTimer;
    private final CallbackRunner callbackRunner;

    public QueueStore(SuperTimer superTimer, CallbackRunner callbackRunner) {
        this.superTimer = superTimer;
        this.callbackRunner = callbackRunner;
        superTimer.schedulePeriod(Duration.ofMinutes(10).toMillis(), this::cleanSentEventMap);
    }

    public SuperTimer getSuperTimer() {
        return superTimer;
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

    public void addEvent(QueueEvent event) {
        WorkerGroup workerGroup = workerGroupByPath(event.getPath());
        addEvent(workerGroup, event);
    }

    public void addEvent(WorkerGroup workerGroup, QueueEvent event) {
        LOGGER.debug("addEvent {} {} => {}", event.getId(), event.getPath(), workerGroup!=null ? workerGroup.prefix : null);
        eventMap.put(event.getId(), event);
        if (workerGroup != null) {
            LOGGER.debug("event without worker {} {}", event.getPath(), event.getId());
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w));
            if (!sent) {
                LOGGER.debug("waiting in queue {}", event.getId());
                workerGroup.events.add(event.getId());
            }
        } else {
            superTimer.schedule(120 * 1000L, event.getId(), this::response503);
        }
    }

    public QueueEvent removeEvent(String eventId){
        return eventMap.remove(eventId);
    }

    private void response503(String requestId){
        response(requestId, 503, null, "<h1>Service Unavailable</h1>".getBytes(StandardCharsets.UTF_8));
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
        if(event.getHandleSend()!=null){
            try{
                event.getHandleSend().run();
            }catch (Exception e){
                LOGGER.warn("event {} handleSend error {}", event.getId(), e.getMessage());
            }
        }
        w.complete(HttpResponse.ok((Object) event.getBody()).headers(new HashMap<>(event.getHeaders())));
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
        QueueEvent event = null;
        while(!workerGroup.events.isEmpty() && event==null) {
            String eventId = workerGroup.events.poll();
            if(eventId!=null){
                event = eventMap.get(eventId);
            }
        }
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
                workerGroup.events.add(event.getId());
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
            if(event.getHandleResponse()!=null){
                event.getHandleResponse().accept(new EventResponse(status, headers, body));
            }
            if (event.getCallback()!=null) {
                if(event.getCallback().headers()!=null) headers.putAll(event.getCallback().headers());
                callbackRunner.callbackAsync(eventId, event.getCallback(), headers, body);
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
