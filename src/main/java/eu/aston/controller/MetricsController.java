package eu.aston.controller;

import java.util.List;
import java.util.Map;

import eu.aston.flow.FlowCounter;
import eu.aston.queue.QueueStat;
import eu.aston.queue.QueueStore;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class MetricsController {

    private final QueueStore queueStore;
    private final FlowCounter flowCounter;

    public MetricsController(QueueStore queueStore, FlowCounter flowCounter) {
        this.queueStore = queueStore;
        this.flowCounter = flowCounter;
    }

    @Get(value = "/metrics", processes = MediaType.TEXT_PLAIN)
    public String metrics(){
        String host = System.getenv("FLOW_INSTANCE_NAME");
        if(host==null) host = "dev";
        StringBuilder sb = new StringBuilder();
        metricsQueue(host, sb);
        metricsWorker(flowCounter.getFlowTotal(), "api_flow_total_sum", sb);
        metricsWorker(flowCounter.getFlowOk(), "api_flow_ok_sum", sb);
        metricsWorker(flowCounter.getFlowError(), "api_flow_error_sum", sb);
        return sb.toString();
    }

    private void metricsQueue(String host, StringBuilder sb) {
        List<QueueStat> stats = queueStore.stats();
        for(QueueStat stat : stats){
            String labels = labels("prefix", stat.prefix(), "host", host);
            sb.append("api_queue_event_count").append(labels).append(' ').append(stat.eventsCount()).append("\n");
            sb.append("api_queue_waiting_events").append(labels).append(' ').append(stat.waitingEvents()).append("\n");
            sb.append("api_queue_worker120").append(labels).append(' ').append(stat.worker120()).append('\n');
            sb.append('\n');
        }
    }

    private void metricsWorker(Map<String, Integer> workerMap, String type, StringBuilder sb) {
        for(Map.Entry<String, Integer> entry : workerMap.entrySet()){
            String labels = labels("flow_type", entry.getKey());
            sb.append(type).append(labels).append(' ').append(entry.getValue()).append("\n");
        }
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
