package eu.aston.queue;

import java.time.Instant;
import java.util.List;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record QueueStat(
        String prefix,
        int eventsCount,
        int waitingEvents,
        Instant oldestEvent,
        Instant lastWorkerCall,
        int worker120,
        List<String> worker120names){}
