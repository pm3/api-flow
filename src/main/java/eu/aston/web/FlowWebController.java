package eu.aston.web;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller("/web/")
@ApiResponse(responseCode = "200", description = "ok")
public class FlowWebController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowWebController.class);

    private final QueryUtils queryUtils;

    public FlowWebController(QueryUtils queryUtils) {
        this.queryUtils = queryUtils;
    }

    @Operation(tags = {"web"})
    @Get("/types")
    public List<String> types(){
        return queryUtils.query1("select distinct caseType from flow_case", List.of());
    }

    @Operation(tags = {"web"})
    @Get("/agg")
    public List<FlowAgg> agg(@QueryValue @Nullable String group,
                             @QueryValue @Nullable String type,
                             @QueryValue @Nullable String state,
                             @QueryValue @Nullable String date){
        LOGGER.info("group: "+group+" type: "+type+" state: "+state+" date: "+date);
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        String groupByPart = null;
        if("type".equals(group)){
            sb.append("select caseType as name, count(*) as count ");
            groupByPart = "caseType";
        } else if("state".equals(group)){
            sb.append("select state as name, count(*) as count ");
            groupByPart = "state";
        } else {
            sb.append("select count(*) as count ");
        }
        sb.append("from flow_case ");
        sb.append("where 1=1 ");
        if(type!=null && !type.isEmpty()){
            sb.append("and caseType=? ");
            params.add(type);
        }
        if(state!=null && !state.isEmpty()){
            sb.append("and state=? ");
            params.add(state);
        }
        filterDate(date, sb, params);

        if(groupByPart!=null){
            sb.append("group by ").append(groupByPart);
        }
        LOGGER.info(sb.toString());
        return queryUtils.query(sb.toString(), params, FlowAgg.class);
    }

    @Operation(tags = {"web"})
    @Get("/cases")
    public List<FlowHead> cases(@QueryValue @Nullable List<String> type,
                                @QueryValue @Nullable List<String> state,
                                @QueryValue @Nullable String date,
                                @QueryValue @Nullable String lastId){
        List<Object> params = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select id, caseType as type, state, created, finished ");
        sb.append("from flow_case ");
        sb.append("where 1=1 ");
        //extends method whereIn
        QueryUtils.whereIn(sb, params, "caseType", type);
        QueryUtils.whereIn(sb, params, "state", state);
        if(lastId!=null && !lastId.isEmpty()){
            sb.append("and created < (select created from flow_case where id=?) ");
            params.add(lastId);
        }
        filterDate(date, sb, params);
        sb.append("order by created desc ");
        sb.append("limit 50 ");
        LOGGER.info(sb.toString());
        return queryUtils.query(sb.toString(), params, FlowHead.class);
    }

    private void filterDate(String date, StringBuilder sb, List<Object> params){
        if(date==null) return;
        if("day".equals(date)){
            sb.append("and created >= date_trunc('day', CURRENT_DATE) ");
        } else if("week".equals(date)){
            sb.append("and created >= date_trunc('week', CURRENT_DATE) ");
        } else if("month".equals(date)){
            sb.append("and created >= date_trunc('month', CURRENT_DATE) ");
        }
        if(date.matches("^\\d{4}-\\d{2}-\\d{2}/\\d{4}-\\d{2}-\\d{2}$")){
            sb.append("and created::text between ? and ? ");
            params.add(date.substring(0,10)+" 00:00:00");
            params.add(date.substring(11,21)+" 23:59:59");
        }
    }
}
