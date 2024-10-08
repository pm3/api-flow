package eu.aston.flow.store;

import java.util.List;
import java.util.Optional;

import eu.aston.flow.model.FlowTask;
import eu.aston.micronaut.sql.aop.Query;
import eu.aston.micronaut.sql.aop.SqlApi;
import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;

@SqlApi
public interface IFlowTaskStore {

    Optional<FlowTaskEntity> loadById(String id);

    void insert(FlowTaskEntity flowCase);

    @Query("""
           update flow_task set
           finished=current_timestamp,
           responseCode=:responseCode,
           response=:response,
           error=null
           where id=:id
           """)
    void finishOk(String id, int responseCode, @Format(JsonConverterFactory.JSON) Object response);

    @Query("""
           update flow_task set
           finished=current_timestamp,
           responseCode=:responseCode,
           response=null,
           error=:error
           where id=:id
           """)
    void finishError(String id, int responseCode, String error);

    @Query("""
           update flow_task set
           queueSent=current_timestamp
           where id=:id
           """)
    void queueSent(String id);

    @Query("select * from flow_task where flowCaseId=:flowCaseId order by created")
    List<FlowTask> selectFlowTaskByCaseId(String flowCaseId);

    @Query("select * from flow_task where flowCaseId=:flowCaseId order by created")
    List<FlowTaskEntity> selectTaskByCaseId(String flowCaseId);

    @Query("delete from flow_task where id=:id")
    void deleteTask(String id);

    @Query("delete from flow_task where flowCaseId=:flowCaseId")
    void deleteTasksByCaseId(String flowCaseId);

    @Query("""
        select id
        from flow_task
        where timeout is not null
        and finished is null
        and created + make_interval(secs => timeout) < current_timestamp
        """)
    List<String> selectExpired();

    @Query("delete from flow_task where finished is null")
    void removeNotFinished();

    @Query("delete from flow_task where id=:id")
    void removeTask(String id);
}
