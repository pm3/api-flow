package eu.aston.web;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class QueryUtils {

    private DataSource dataSource;

    public QueryUtils(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void whereIn(StringBuilder sb, List<Object> params, String column, List<String> values){
        if(values!=null && !values.isEmpty()){
            sb.append("and ").append(column).append(" in (").append(String.join(",", values)).append(") ");
        }
    }

    public <T> List<T> query(String sql, List<Object> params, Class<T> beanClass, String[] columns) {
        BeanIntrospection<T> beanIntrospection = BeanIntrospector.SHARED.getIntrospection(beanClass);
        try(Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for(int i=0; i<params.size(); i++){
                    ps.setObject(i+1, params.get(i));
                }
                List<T> l = new ArrayList<>();
                try(ResultSet rs = ps.executeQuery()){
                    while(rs.next()){
                        l.add(rsToBean(rs, beanIntrospection, columns));
                    }
                }
                return l;
            }
        }catch (SQLException e){
            e.printStackTrace();
            return List.of();
        }
    }

    private <T> T rsToBean(ResultSet rs, BeanIntrospection<T> beanIntrospection, String[] columns) {
        BeanIntrospection.Builder<T> b = beanIntrospection.builder();
        for(String column : columns){
            try{
                Object value = rs.getObject(column);
                if(value instanceof Timestamp){
                    b.with(column, ((Timestamp) value).toInstant());
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