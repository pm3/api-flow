package eu.aston.flow.ognl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ognl.Ognl;
import ognl.OgnlException;

@SuppressWarnings({"rawtypes", "unchecked"})
public class FlowScript {

    private static final ConcurrentHashMap<String, Object> expressions = new ConcurrentHashMap<>();
    private final Map<String, Object> root;
    private final Map ctx;

    public FlowScript(Map<String, Object> root) {
        this.root = root!=null ? root : new HashMap<>();
        this.ctx = Ognl.createDefaultContext(this.root);
    }

    public boolean execWhere(String expr) throws OgnlException {
        if(expr==null) return false;
        Object r = execExpr(expr);
        if (r instanceof Boolean b) return b;
        if (r instanceof Number n) return n.intValue() != 0;
        if (r instanceof String s) return !s.isEmpty();
        if (r instanceof List<?> l) return !l.isEmpty();
        if (r instanceof Map<?,?> m) return !m.isEmpty();
        return r!=null;
    }

    public Map<String, Object> execMap(Map mapExpr) throws OgnlException {
        if(mapExpr==null || mapExpr.isEmpty()) return null;
        Map<String, Object> m2 = new HashMap<>();
        for(Map.Entry e : ((Map<Object,Object>)mapExpr).entrySet()){
            String key = e.getKey().toString();
            if(key.startsWith("$$")) {
                m2.put(key.substring(1), e.getValue());
                continue;
            }
            if(key.startsWith("$")) {
                if (e.getValue() instanceof String expr) {
                    m2.put(key.substring(1), execExpr(expr));
                    continue;
                }
            }
            if(e.getValue() instanceof Map map2) {
                m2.put(key, execMap(map2));
                continue;
            }
            if(e.getValue()!=null) {
                m2.put(key, e.getValue());
            }
        }

        return m2;
    }

    public Map<String, String> execMapS(Map<String, String> mapExpr) throws OgnlException {
        if(mapExpr==null || mapExpr.isEmpty()) return null;
        Map<String, String> m2 = new HashMap<>();
        for(Map.Entry<String, String> e: mapExpr.entrySet()){
            if(e.getKey().startsWith("$") && e.getValue()!=null){
                Object val = execExpr(e.getValue());
                if(val!=null){
                    m2.put(e.getKey().substring(1), val.toString());
                }
            } else {
                m2.put(e.getKey(), e.getValue());
            }
        }
        return m2;
    }

    public Object execExpr(String expr) throws OgnlException {
        if(expr==null) return null;
        Object expr2 = expressions.computeIfAbsent(expr, this::parseExpr);
        if(expr2==null) return null;
        try{
            return Ognl.getValue(expr2, ctx, root);
        }catch (OgnlException e){
            if(e.getReason() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    private Object parseExpr(String expr) {
        try{
            return Ognl.parseExpression(expr);
        }catch (Exception e){
            throw new RuntimeException("parse expression "+expr, e);
        }
    }

    public static class LazyMap extends HashMap<String, Object> {
        @Override
        public Object get(Object key) {
            Object val = super.get(key);
            if(val instanceof RuntimeException ex) throw ex;
            return val;
        }
    }
}