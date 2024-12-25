package eu.aston;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.aston.flow.model.FlowCase;
import eu.aston.flow.ognl.OgnlFlow;
import eu.aston.flow.ognl.OgnlFlowFactory;
import eu.aston.flow.store.FlowCaseEntity;
import eu.aston.flow.store.FlowTaskEntity;
import eu.aston.flow.task.TaskHttpRequest;
import org.junit.jupiter.api.Test;

public class OgnlFlowTest {

    public static void main(String[] args) {
        OgnlFlowTest test = new OgnlFlowTest();
        try {
            test.loadFlow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void loadFlow() throws Exception {

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);


        ObjectMapper objectMapper = new ObjectMapper();
        AppConfig appConfig = new AppConfig();
        OgnlFlowFactory ognlFlowFactory = new OgnlFlowFactory(objectMapper, appConfig);
        OgnlFlow ognlFlow = (OgnlFlow) ognlFlowFactory.createFlow(new File("test_root/flow1.flow.yaml"));

        FlowCaseEntity flowCase = new FlowCaseEntity();
        flowCase.setId("1");
        flowCase.setState(FlowCase.CREATED);
        List<FlowTaskEntity> tasks = new ArrayList<>();
        List<TaskHttpRequest> requests = ognlFlow.execTick(flowCase, tasks);
        System.out.println("tasks");
        System.out.println(tasks.size());
        System.out.println(tasks);
        System.out.println("requests");
        System.out.println(requests.size());
        System.out.println(requests);
        System.out.println("state "+flowCase.getState());
    }
}
