package pl.netia.troubleticket.security;

import pl.netia.troubleticket.ticket.domain.TenantId;

/**
 * The caller as established by a verified access token: its tenant scope and its subject (user or API account).
 */
public record AuthenticatedClient(TenantId tenantId, String subject) {
}
