package eu.aston.flow.store;

import java.util.List;
import java.util.Optional;

import eu.aston.flow.model.FlowCase;
import eu.aston.micronaut.sql.aop.Query;
import eu.aston.micronaut.sql.aop.SqlApi;
import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;

@SqlApi
public interface IFlowCaseStore {

    Optional<FlowCaseEntity> loadById(String id);

    void insert(FlowCaseEntity flowCase);

    @Query("""
           update flow_case
           set finished=current_timestamp,
           response=:response,
           state=:state
           where id=:id
           """)
    void finishFlow(String id, String state, @Format(JsonConverterFactory.JSON) Object response);

    @Query("""
           update flow_case
           set state=:state
           where id=:id
           """)
    void updateFlowState(String id, String state);

    @Query("""
           select *
           from flow_case
           where id=:id
           """)
    FlowCase loadFlowCaseById(String id);

    @Query("select id from flow_case where finished is null")
    List<String> selectIdForAllNotFinished();
}
