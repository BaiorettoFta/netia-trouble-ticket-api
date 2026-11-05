package pl.netia.troubleticket.ticket.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import pl.netia.troubleticket.ticket.domain.ServiceInventory;
import pl.netia.troubleticket.ticket.domain.TenantId;

/**
 * Reads the local service_instance table, a stand-in for the real inventory system.
 */
@Component
class JdbcServiceInventory implements ServiceInventory {

    private final JdbcClient jdbc;

    JdbcServiceInventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActiveServiceOf(TenantId tenantId, long serviceId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from service_instance
                            where id = :serviceId and tenant_id = :tenantId and status = 'active')
                        """)
                .param("serviceId", serviceId)
                .param("tenantId", tenantId.value())
                .query(Boolean.class)
                .single();
    }
}
