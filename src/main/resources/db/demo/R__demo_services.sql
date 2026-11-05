-- Demo services for the local environment only (profile "local"); they never enter the production schema history.
-- The ids are those of the contract examples, so the request examples from trouble-ticket-api.yaml work as they are.
insert into service_instance (id, tenant_id, status) values
    (987654321, 'tenant-a', 'active'),   -- serviceId of the create request example
    (987654322, 'tenant-a', 'active'),   -- serviceId of the list response example
    (987654329, 'tenant-a', 'inactive'), -- shows SERVICE_NOT_FOUND for an inactive service
    (987654331, 'tenant-b', 'active')    -- shows SERVICE_NOT_FOUND for a service of another tenant
on conflict (id) do update set tenant_id = excluded.tenant_id, status = excluded.status;
