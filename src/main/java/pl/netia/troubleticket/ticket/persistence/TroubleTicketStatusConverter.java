package pl.netia.troubleticket.ticket.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;

/**
 * Stores statuses as the codes used by the API ({@code inProgress}, not {@code IN_PROGRESS}); applied to every
 * status attribute automatically.
 */
@Converter(autoApply = true)
class TroubleTicketStatusConverter implements AttributeConverter<TroubleTicketStatus, String> {

    @Override
    public String convertToDatabaseColumn(TroubleTicketStatus status) {
        return status == null ? null : status.code();
    }

    @Override
    public TroubleTicketStatus convertToEntityAttribute(String code) {
        return code == null ? null : TroubleTicketStatus.fromCode(code);
    }
}
