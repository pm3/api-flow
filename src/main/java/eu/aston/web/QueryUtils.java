package eu.aston.web;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.naming.Named;
import jakarta.inject.Singleton;

@Singleton
public class QueryUtils {

    private final DataSource dataSource;

    public QueryUtils(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void whereIn(StringBuilder sb, List<Object> params, String column, List<String> values){
        if(values!=null && !values.isEmpty()){
            sb.append("and ").append(column).append(" in (");
            sb.append("?,".repeat(values.size()-1));
            sb.append("?) ");
            params.addAll(values);
        }
    }

    public <T> List<T> query(String sql, List<Object> params, Class<T> beanClass) {
        BeanIntrospection<T> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(beanClass);
        try(Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for(int i=0; i<params.size(); i++){
                    ps.setObject(i+1, params.get(i));
                }
                List<T> l = new ArrayList<>();
                try(ResultSet rs = ps.executeQuery()){
                    Map<String, Type> columns = rsColumns(rs, beanIntrospection);
                    while(rs.next()){
                        l.add(rsToBean(rs, beanIntrospection, columns));
                    }
                }
                return l;
            }
        }catch (SQLException e){
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public List<String> query1(String sql, List<Object> params) {

        try(Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for(int i=0; i<params.size(); i++){
                    ps.setObject(i+1, params.get(i));
                }
                List<String> l = new ArrayList<>();
                try(ResultSet rs = ps.executeQuery()){
                    while(rs.next()){
                        l.add(rs.getString(1));
                    }
                }
                return l;
            }
        }catch (SQLException e){
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Map<String, Type> rsColumns(ResultSet rs, BeanIntrospection<?> beanIntrospection) throws SQLException {
        List<String> rsColumns = new ArrayList<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 0; i < rsmd.getColumnCount(); i++) {
            rsColumns.add(rsmd.getColumnName(i + 1).toLowerCase());
        }
        Map<String, Type> columns = new HashMap<>();
        for(BeanProperty<?,?> bp : beanIntrospection.getBeanProperties()){
            if(bp instanceof Named named){
                if(rsColumns.contains(named.getName().toLowerCase())){
                    columns.put(named.getName(), bp.getType());
                }
            }
        }
        return columns;
    }
    private <T> T rsToBean(ResultSet rs, BeanIntrospection<T> beanIntrospection, Map<String, Type> columns) {
        BeanIntrospection.Builder<T> b = beanIntrospection.builder();
        for(String column : columns.keySet()){
            try{
                Object value = rs.getObject(column);
                Type type = columns.get(column);
                if(value instanceof Timestamp){
                    b.with(column, ((Timestamp) value).toInstant());
                } else if (type.equals(Integer.class) && value instanceof Number){
                    b.with(column, ((Number) value).intValue());
                } else if (type.equals(Long.class) && value instanceof Number){
                    b.with(column, ((Number) value).longValue());
                } else if(value!=null){
                    b.with(column, value);
                }
            }catch (SQLException e){
                e.printStackTrace();
            }
        }
        return b.build();
    }
}