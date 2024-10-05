package eu.aston.controller;

import java.util.List;

import eu.aston.queue.QueueStat;
import eu.aston.queue.QueueStore;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class MetricsController {

    private final QueueStore queueStore;

    public MetricsController(QueueStore queueStore) {
        this.queueStore = queueStore;
    }

    @Get(value = "/metrics", processes = MediaType.TEXT_PLAIN)
    public String metrics(){
        List<QueueStat> stats = queueStore.stats();
        String host = System.getenv("FLOW_INSTANCE_NAME");
        if(host==null) host = "dev";
        StringBuilder sb = new StringBuilder();
        for(QueueStat stat : stats){
            String labels = labels("prefix", stat.prefix(), "host", host);
            sb.append("api_flow_worker_event_count").append(labels).append(' ').append(stat.eventsCount()).append("\n");
            sb.append("api_flow_worker_waiting_events").append(labels).append(' ').append(stat.waitingEvents()).append("\n");
            sb.append("api_flow_worker120").append(labels).append(' ').append(stat.worker120()).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private String labels(String... labels) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for(int i=0; i<labels.length; i+=2){
            if(i>0) sb.append(',');
            sb.append(labels[i]).append('=').append('"').append(labels[i+1]).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

}
