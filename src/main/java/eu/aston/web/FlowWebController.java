package eu.aston.web;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller("/flow/web/")
@ApiResponse(responseCode = "200", description = "ok")
public class FlowWebController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowWebController.class);

    private QueryUtils queryUtils;
    private final Map<String, String> GROUP_BY_PARTS = Map.of(
        "type", "type",
        "state", "state",
        "hour", "EXTRACT(HOUR FROM created)",
        "day", "EXTRACT(DAY FROM created)",
        "month", "EXTRACT(MONTH FROM created)"
    );

    public FlowWebController(QueryUtils queryUtils) {
        this.queryUtils = queryUtils;
    }

    @Operation(tags = {"web"})
    @Get("/agg")
    public List<FlowAgg> agg(@QueryValue String groupBy,
                             @QueryValue String type,
                             @QueryValue String state){
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        String[] groupByParts = null;
        sb.append("select ");
        if(groupBy!=null && !groupBy.isEmpty()){
            groupByParts = groupBy.split(",");
            for(int i=0; i<groupByParts.length; i++){
                String expr = GROUP_BY_PARTS.get(groupByParts[i]);
                if(expr!=null){
                    sb.append(expr).append(" as ").append(groupByParts[i]).append(",");
                }
            }
            sb.append("count(*) as count");
        }
        sb.append("from flow_case ");
        sb.append("where 1=1 ");
        if(groupByParts!=null && groupBy.contains("hour")){
            //last 24 houurs
            sb.append("and created >= now() - interval '24 hours' ");
        } else if(groupByParts!=null && groupBy.contains("day")){
            //last 28 days
            sb.append("and created >= now() - interval '28 days' ");
        } else {
            //last 12 months
            sb.append("and created >= now() - interval '12 months' ");
        }
        if(type!=null && !type.isEmpty()){
            sb.append("and flowType=?");
            params.add(type);
        }
        if(state!=null && !state.isEmpty()){
            sb.append("and state=?");
            params.add(state);
        }
        if(groupByParts!=null && groupByParts.length>0){
            sb.append("group by ");
            for(int i=0; i<groupByParts.length; i++){
                String expr = GROUP_BY_PARTS.get(groupByParts[i]);
                if(expr!=null){
                    if(i>0) sb.append(",");
                    sb.append(expr);
                }
            }
        }
        LOGGER.info(sb.toString());
        return queryUtils.query(sb.toString(), params, FlowAgg.class, new String[]{"type", "state", "hour", "day", "month", "count"});
    }

    @Operation(tags = {"web"})
    @Get("/flows")
    public List<FlowHead> flows(@QueryValue List<String> type,
                                @QueryValue List<String> state,
                                @QueryValue String date,
                                @QueryValue Integer page){
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select id, flowType, state, created, finished, finished - created as duration");
        sb.append("from flow_case ");
        sb.append("where 1=1 ");
        //extends method whereIn
        QueryUtils.whereIn(sb, params, "flowType", type);
        QueryUtils.whereIn(sb, params, "state", state);
        if(date!=null && !date.isEmpty()){
            sb.append("and created >= ? ");
            params.add(date);
        }
        sb.append("order by created desc ");

        if(page!=null && page>1) {
            sb.append("limit 20 offset ? ");
            params.add((page-1)*20);
        } else {
            sb.append("limit 20 ");
        }
        LOGGER.info(sb.toString());
        return queryUtils.query(sb.toString(), params, FlowHead.class, new String[]{"id", "flowType", "state", "created", "finished", "duration"});
    }
}
