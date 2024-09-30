package eu.aston;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import eu.aston.flow.ognl.FlowScript;
import eu.aston.flow.ognl.RuntimeExceptionSerializer;
import eu.aston.flow.ognl.TaskResponseException;
import eu.aston.flow.ognl.WaitingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class FlowScriptTest {

    private static FlowScript createCtx(){
        Map<String, Object> root = new FlowScript.LazyMap();
        root.put("a", "a");
        root.put("b", 1);
        root.put("c", Map.of("c1", "c1", "c2", "c2"));
        root.put("d", List.of("a", "b", "c"));
        root.put("e", new WaitingException("e"));
        root.put("f", new TaskResponseException("f"));
        root.put("g", Map.of("a", new TaskResponseException("f")));
        return new FlowScript(root);
    }

    @Test
    public void testWhere(){

        FlowScript flowScript = createCtx();

        Assertions.assertDoesNotThrow(()->{
            boolean ok1 = flowScript.execWhere("b!=2");
            Assertions.assertTrue(ok1, "where true");
        });

        Assertions.assertDoesNotThrow(()->{
            boolean ok1 = flowScript.execWhere("x==3");
            Assertions.assertFalse(ok1, "where false");
        });

        Assertions.assertThrows(WaitingException.class, ()->{
           boolean ok1 = flowScript.execWhere("e.e=1");
            System.out.println(ok1);
        });

        Assertions.assertThrows(TaskResponseException.class, ()->{
            boolean ok1 = flowScript.execWhere("f.a");
        });
    }

    @Test
    public void testParamsMap(){


        FlowScript flowScript = createCtx();


        Assertions.assertDoesNotThrow(()->{
            Map<String, Object> params = new HashMap<>();
            params.put("a", "aa");
            params.put("$b", "b");
            params.put("$$dd", "dd");
            Map<String, Object> values = flowScript.execMap(params);
            Assertions.assertEquals("aa", values.get("a"));
            Assertions.assertEquals(1, values.get("b"));
            Assertions.assertNull(values.get("c"));
            Assertions.assertEquals("dd", values.get("$dd"));
        });

        Assertions.assertThrows(WaitingException.class, ()->{
            Map<String, Object> params = new HashMap<>();
            params.put("$a", "a");
            params.put("$e", "e");
            Map<String, Object> values = flowScript.execMap(params);
        });

        Assertions.assertThrows(TaskResponseException.class, ()->{
            Map<String, Object> params = new HashMap<>();
            params.put("$g", "g");
            Map<String, Object> values = flowScript.execMap(params);
            Assertions.assertInstanceOf(Map.class, values.get("g"));
            try{
                ObjectMapper objectMapper = new ObjectMapper();
                SimpleModule module = new SimpleModule();
                module.addSerializer(TaskResponseException.class, new RuntimeExceptionSerializer());
                objectMapper.registerModule(module);
                String json = objectMapper.writeValueAsString(values);
            }catch (JsonMappingException e){
                if(e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        });

        Assertions.assertDoesNotThrow(()->{
            Map<String, Object> params = new HashMap<>();
            params.put("$.", "a");
            Map<String, Object> values = flowScript.execMap(params);
            Assertions.assertTrue(values.size()==1 && values.containsKey("."));
        });

    }
}
