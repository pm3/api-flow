package eu.aston.flow.store;

import java.util.List;
import java.util.Optional;

import eu.aston.flow.model.FlowTask;
import eu.aston.micronaut.sql.aop.Query;
import eu.aston.micronaut.sql.aop.SqlApi;
import eu.aston.micronaut.sql.convert.JsonConverterFactory;
import eu.aston.micronaut.sql.entity.Format;

@SqlApi
public interface IFlowRequestStore {

    void insert(FlowRequestEntity flowRequest);

    @Query("select * from flow_request where id=:id")
    FlowRequestEntity loadById(String id);

    @Query("select * from flow_request where flowCaseId=:flowCaseId")
    List<FlowRequestEntity> selectByCaseId(String flowCaseId);

    @Query("delete from flow_request where flowCaseId=:flowCaseId")
    void deleteByCaseId(String flowCaseId);
}
