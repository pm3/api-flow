package eu.aston.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        superTimer.schedulePeriodTaskCreator(Duration.ofSeconds(1).toMillis(), this::timeoutSlowWorkers);
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
        LOGGER.debug("addEvent {} {} => workerGroup {}", event.getId(), event.getPath(), workerGroup!=null ? workerGroup.prefix : null);
        eventMap.put(event.getId(), event);
        if (workerGroup != null) {
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w, workerGroup));
            if (!sent) {
                LOGGER.debug("waiting in queue {}", event.getId());
                workerGroup.events.add(event.getId());
            }
        } else {
            LOGGER.debug("event without worker {} {}", event.getPath(), event.getId());
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

    private void sendRemoteEvent(QueueEvent event, CompletableFuture<HttpResponse<?>> w, WorkerGroup workerGroup) {
        workerGroup.eventCounter.incrementAndGet();
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
        long now = System.currentTimeMillis();
        workerGroup.lastWorker = now;
        if(worker.getSlow()>0) {
            workerGroup.lastWorkerPing.put(worker.getId()+"@slow", now);
            // check if fast worker is still alive
            if(workerGroup.lastWorkerFast+worker.getSlow()*1000L>now){
                workerGroup.slowWorkers.add(worker);
                return;
            }
        } else {
            workerGroup.lastWorkerPing.put(worker.getId(), now);
            workerGroup.lastWorkerFast = now;
        }
        QueueEvent event = null;
        while(!workerGroup.events.isEmpty() && event==null) {
            String eventId = workerGroup.events.poll();
            if(eventId!=null){
                event = eventMap.get(eventId);
            }
        }
        if (event != null) {
            sendRemoteEvent(event, worker.removeResponse(), workerGroup);
        } else if(worker.getSlow()==0) {
            workerGroup.workers.add(worker);
            superTimer.schedule(worker.getTimeout() * 1000L, worker, this::timeoutWorker);
        } else {
            workerGroup.slowWorkers.add(worker);
        }
    }

    private void workerGroupAddEvents(WorkerGroup workerGroup, List<QueueEvent> list) {
        list.sort((e1,e2)->(int)(e1.getT1()-e2.getT1()));
        for (QueueEvent event : list){
            boolean sent = nextWorker(workerGroup, (w) -> sendRemoteEvent(event, w , workerGroup));
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

    private void timeoutSlowWorkers(Consumer<Runnable> executor) {
        for (WorkerGroup wg : new ArrayList<>(workerTree.values())) {
            for(Iterator<Worker> it = wg.slowWorkers.iterator(); it.hasNext(); ) {
                Worker w = it.next();
                if(w.created+w.getTimeout()*1000L < System.currentTimeMillis()) {
                    it.remove();
                    executor.accept(()->this.timeoutWorker(w));
                    continue;
                }
                QueueEvent event = slowEvent(wg, w.getSlow()*1000L);
                if(event!=null){
                    it.remove();
                    executor.accept(()->sendRemoteEvent(event, w.removeResponse(), wg));
                }
            }
        }
    }

    private QueueEvent slowEvent(WorkerGroup wg, long slow) {
        String eventId = wg.events.peek();
        if (eventId != null) {
            QueueEvent event = eventMap.get(eventId);
            if (event != null) {
                if(System.currentTimeMillis()-event.getT1()>slow || System.currentTimeMillis()-wg.lastWorkerFast>slow){
                    String eventId2 = wg.events.poll();
                    if(eventId2!=null) {
                        return eventMap.get(eventId2);
                    }
                }
            }
        }
        return null;
    }

    public void response(String eventId, int status, Map<String, String> headers, byte[] body) {
        QueueEvent event = eventMap.remove(eventId);
        if (event != null) {
            event.setT3(status);
            LOGGER.debug("event response {}{} {}", eventId, event.getPath(), status);
            if(LOGGER.isDebugEnabled()) LOGGER.debug("event response body {}", new String(body));
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

    public List<QueueStat> stats() {
        Instant now = Instant.now();
        List<WorkerGroup> groups = new ArrayList<>(workerTree.values());
        return groups.stream().map(wg->createStat(wg, now)).toList();
    }

    private QueueStat createStat(WorkerGroup wg, Instant now) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(wg.lastWorkerPing.entrySet());
        long expired = System.currentTimeMillis()-120_000;
        for(Map.Entry<String, Long> e: entries){
            if(e.getValue()<expired) wg.lastWorkerPing.remove(e.getKey(), e.getValue());
        }
        Long oldestEvent = Optional.ofNullable(wg.events.peek()).map(eventMap::get).map(QueueEvent::getT1).orElse(null);
        return new QueueStat(
                wg.prefix,
                wg.eventCounter.get(),
                wg.events.size(),
                oldestEvent!=null ? Instant.ofEpochMilli(oldestEvent) : null,
                Instant.ofEpochMilli(wg.lastWorker),
                wg.lastWorkerPing.size(),
                new ArrayList<>(wg.lastWorkerPing.keySet())
        );
    }

}
