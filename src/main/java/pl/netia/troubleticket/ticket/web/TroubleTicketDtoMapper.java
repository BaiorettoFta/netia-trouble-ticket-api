package pl.netia.troubleticket.ticket.web;

import java.time.ZoneOffset;
import pl.netia.troubleticket.openapi.model.NoteDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketStatusDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketSummaryDto;
import pl.netia.troubleticket.ticket.application.NoteView;
import pl.netia.troubleticket.ticket.application.TroubleTicketView;
import pl.netia.troubleticket.ticket.domain.TroubleTicketStatus;
import pl.netia.troubleticket.ticket.domain.TroubleTicketSummary;

/**
 * Maps application views to the DTOs generated from the contract. Times are written in UTC.
 */
final class TroubleTicketDtoMapper {

    private TroubleTicketDtoMapper() {
    }

    static TroubleTicketDto toDto(TroubleTicketView ticket) {
        return new TroubleTicketDto(ticket.id(), ticket.externalId(), ticket.serviceId(), ticket.description(),
                toDto(ticket.status()), ticket.notes().stream().map(TroubleTicketDtoMapper::toDto).toList());
    }

    static TroubleTicketSummaryDto toDto(TroubleTicketSummary summary) {
        return new TroubleTicketSummaryDto(summary.externalId(), summary.serviceId(), summary.description(),
                toDto(summary.status()));
    }

    static NoteDto toDto(NoteView note) {
        return new NoteDto(note.id(), note.text(), note.date().atOffset(ZoneOffset.UTC));
    }

    static TroubleTicketStatusDto toDto(TroubleTicketStatus status) {
        return TroubleTicketStatusDto.fromValue(status.code());
    }
}
