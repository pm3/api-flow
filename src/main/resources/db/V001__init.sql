create table flow_case (

    id varchar(32) not null primary key,
    caseType varchar(32) not null,
    externalId varchar(128),
    callback text,
    params text,
    assets text,
    response text,
    created timestamp not null,
    finished timestamp,
    state varchar(128) not null
);

create table flow_task (
    id varchar(32) not null primary key,
    flowCaseId  varchar(32) not null,
    step varchar(64) not null,
    worker varchar(64) not null,
    stepIndex int,
    responseCode int,
    response text,
    error text,
    timeout int,
    created timestamp not null,
    started timestamp,
    finished timestamp,
    queueSent timestamp
);

create index flow_task_case_id on flow_task(flowCaseId);