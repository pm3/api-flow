
create table flow_request (
    id varchar(32) not null primary key,
    flowCaseId  varchar(32) not null,
    method varchar(32) not null,
    path varchar(1024) not null,
    body text
);

create index flow_request_case_id on flow_request(flowCaseId);