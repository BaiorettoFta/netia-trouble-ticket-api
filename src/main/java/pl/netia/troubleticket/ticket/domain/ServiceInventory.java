package pl.netia.troubleticket.ticket.domain;

/**
 * Port to the inventory of customer services. In this service it is backed by a local table; in production it
 * would call the inventory system.
 */
public interface ServiceInventory {

    /**
     * Whether the service exists, is active and belongs to the tenant. The three cases are deliberately not told
     * apart, so a caller cannot learn about services of other tenants.
     */
    boolean isActiveServiceOf(TenantId tenantId, long serviceId);
}
