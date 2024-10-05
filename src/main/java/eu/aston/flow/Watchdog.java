package eu.aston.flow;

import java.time.Duration;
import java.util.List;

import eu.aston.utils.SuperTimer;
import io.micronaut.context.annotation.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Context
public class Watchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger(Watchdog.class);

    private final FlowCaseManager flowCaseManager;

    public Watchdog(FlowCaseManager flowCaseManager, SuperTimer superTimer) {
        this.flowCaseManager = flowCaseManager;
        superTimer.execute(this::fixKilled);
        superTimer.schedulePeriod(Duration.ofMinutes(1).toMillis(), this::watchdogTimeoutTasks);
    }

    private void fixKilled() {
        flowCaseManager.getTaskStore().removeNotFinished();
        List<String> notFinishedCases = flowCaseManager.getCaseStore().selectIdForAllNotFinished();
        for(String id : notFinishedCases){
            LOGGER.info("restart flow case {}", id);
            flowCaseManager.getFlowThreadPool().addCase(id, id);
        }
        watchdogTimeoutTasks();
    }

    private void watchdogTimeoutTasks() {
        List<String> expiredTasks = flowCaseManager.getTaskStore().selectExpired();
        for (String id : expiredTasks){
            LOGGER.info("watchdog timeout task {}", id);
            flowCaseManager.finishTask(id, 408, "timeout-watchdog");
        }
    }

}
