alter table flow_case add column step varchar(128);

update flow_case set step = substring(state from 6) where state like 'step-%';
update flow_case set state = 'WORKING' where state like 'step-%';