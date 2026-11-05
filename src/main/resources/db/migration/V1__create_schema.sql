-- Numbers for public trouble ticket ids such as TT-2026-000001.
create sequence trouble_ticket_number_seq;

-- Stand-in for the service inventory: which services exist, whether they are active and which tenant owns them.
-- In production this information would come from the inventory system (for example TMF638), so trouble tickets
-- deliberately have no foreign key to this table.
create table service_instance (
    id        bigint      primary key,
    tenant_id varchar(64) not null,
    status    varchar(16) not null,
    constraint ck_service_instance_status check (status in ('active', 'inactive'))
);

create table trouble_ticket (
    id          varchar(32)  primary key,
    tenant_id   varchar(64)  not null,
    external_id varchar(255) not null,
    service_id  bigint       not null,
    description text         not null,
    status      varchar(16)  not null,
    created_by  varchar(255) not null,
    created_at  timestamptz  not null,
    updated_by  varchar(255) not null,
    updated_at  timestamptz  not null,
    -- Idempotency key of the create operation; also resolves concurrent creates with the same key.
    constraint uq_trouble_ticket_tenant_external_id unique (tenant_id, external_id),
    constraint ck_trouble_ticket_status
        check (status in ('new', 'acknowledged', 'inProgress', 'resolved', 'closed', 'rejected'))
);

-- The list operation reads all tickets of a tenant in creation order.
create index ix_trouble_ticket_tenant_created on trouble_ticket (tenant_id, created_at, id);

create table trouble_ticket_note (
    id                bigint generated always as identity primary key,
    trouble_ticket_id varchar(32)  not null,
    text              text         not null,
    created_by        varchar(255) not null,
    created_at        timestamptz  not null,
    constraint fk_trouble_ticket_note_ticket foreign key (trouble_ticket_id) references trouble_ticket (id)
);

-- PostgreSQL does not index foreign keys by itself; notes are always read per ticket in insertion order.
create index ix_trouble_ticket_note_ticket on trouble_ticket_note (trouble_ticket_id, id);
