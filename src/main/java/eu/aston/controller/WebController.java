package eu.aston.controller;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import eu.aston.flow.model.FlowCase;
import eu.aston.flow.model.WebCase;
import eu.aston.flow.model.WebCaseQuery;
import eu.aston.flow.store.IFlowCaseStore;
import eu.aston.flow.store.IFlowRequestStore;
import eu.aston.flow.store.IFlowTaskStore;
import eu.aston.utils.QuerySql;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;

@Controller("/flow-web")
public class WebController {

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(WebController.class);

    private final IFlowCaseStore caseStore;
    private final IFlowTaskStore taskStore;
    private final IFlowRequestStore requestStore;
    private final DataSource dataSource;
    private final QuerySql querySql;

    public WebController(IFlowCaseStore caseStore,
                         IFlowTaskStore taskStore,
                         IFlowRequestStore requestStore,
                         DataSource dataSource) {
        this.caseStore = caseStore;
        this.taskStore = taskStore;
        this.requestStore = requestStore;
        this.dataSource = dataSource;
        this.querySql = new QuerySql("flow_case");
        querySql.addColumnGroup("caseType", "caseType");
        querySql.addColumnGroup("state", "state");
        querySql.addColumnGroup("hour", "to_char(created, 'YYYY-MM-DD HH24')");
        querySql.addColumnAgg("count", "count(*)");
        querySql.addColumnAgg("duration", "sum(finished-created)");
        querySql.addColumnUnique("created", "crated");
        querySql.addColumnUnique("finished", "finished");
        querySql.addColumnUnique("id", "id");
    }

    @Operation(tags = {"web"})
    @Get("/case/{id}")
    public FlowCase fetchCase(@PathVariable String id){
        return null;
    }

    @Operation(tags = {"web"})
    @Get("/case")
    public List<WebCase> fetchCase(@PathVariable WebCaseQuery query){

        List<WebCase> cases = new ArrayList<>();
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        parseWhere(where, params, query);

        String sql = querySql.createSqlQuery(query.getSelect(), where);
        try(Connection c = dataSource.getConnection()){
            try(PreparedStatement ps = c.prepareStatement(sql)){
                for (int i = 0; i<params.size(); i++){
                    ps.setObject(i+1, params.get(i));
                }
                try(var rs = ps.executeQuery()){
                    while(rs.next()){
                        WebCase flowCase = new WebCase();
                        flowCase.setId(rs.getString("id"));
                        flowCase.setCaseType(rs.getString("caseType"));
                        flowCase.setState(rs.getString("state"));
                        flowCase.setCreated(rs.getTimestamp("created").toInstant());
                        flowCase.setFinished(rs.getTimestamp("finished").toInstant());
                        flowCase.setCount(rs.getInt("count"));
                        flowCase.setDuration(rs.getInt("duration"));
                        cases.add(flowCase);
                    }
                }
            }
        } catch (Exception e){
            logger.error("Error fetching cases", e);
        }
        return cases;
    }

    private void parseWhere(List<String> where, List<Object> params, WebCaseQuery query) {
        if (query.getDateFrom() != null){
            where.add("created >= ?");
            params.add(query.getDateFrom());
        }
        if (query.getDateTo() != null){
            where.add("created <= ?");
            params.add(query.getDateTo());
        }
        if (query.getCaseType() != null){
            whereIn(where, params, "caseType", query.getCaseType());
        }
        if (query.getState() != null){
            where.add("state = ?");
            whereIn(where, params, "state", query.getCaseType());
        }
        if (query.getDuration() > 0){
            where.add("finished-created > ?");
            params.add(query.getDuration());
        }
    }

    private void whereIn(List<String> where, List<Object> params, String columnName, String value) {
        if(value!=null && !value.isEmpty()){
            StringBuilder sb = new StringBuilder();
            sb.append(columnName).append(" in (");
            String[] split = value.split(",");
            for (int i = 0; i<split.length; i++){
                if(i>0) {
                    sb.append(",");
                }
                sb.append("?");
                params.add(split[i]);
            }
            sb.append(")");
            where.add(sb.toString());
        }
    }
}
