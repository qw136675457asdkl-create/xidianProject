create table SYSTEM_COLLABORATION_TEST.TASK_DATA_METRIC
(
    id bigint identity(1,1) primary key,
    task_data_group_id bigint not null,
    field_name varchar(128) not null,
    data_type varchar(64),
    recommended_value varchar(255),
    fluctuation_range varchar(255),
    description varchar(1000),
    sort_no int
);

create index IDX_TASK_DATA_METRIC_GROUP_ID
    on SYSTEM_COLLABORATION_TEST.TASK_DATA_METRIC (task_data_group_id);
