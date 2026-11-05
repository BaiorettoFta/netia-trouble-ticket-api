package pl.netia.troubleticket.ticket.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.netia.troubleticket.openapi.api.TroubleTicketApi;
import pl.netia.troubleticket.openapi.model.TroubleTicketCloseStatusRequestDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketCreateRequestDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketDto;
import pl.netia.troubleticket.openapi.model.TroubleTicketSummaryDto;
import pl.netia.troubleticket.security.AuthenticatedClient;
import pl.netia.troubleticket.security.CurrentClient;
import pl.netia.troubleticket.ticket.application.CreationResult.AlreadyExists;
import pl.netia.troubleticket.ticket.application.CreationResult.Created;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;
import pl.netia.troubleticket.ticket.application.TroubleTicketView;
import pl.netia.troubleticket.ticket.domain.NewTroubleTicket;
import pl.netia.troubleticket.web.ApiPaths;

/**
 * Implements the interface generated from the contract, so paths, methods and types cannot drift from it.
 */
@RestController
class TroubleTicketController implements TroubleTicketApi {

    private final TroubleTicketService service;
    private final CurrentClient currentClient;

    TroubleTicketController(TroubleTicketService service, CurrentClient currentClient) {
        this.service = service;
        this.currentClient = currentClient;
    }

    @Override
    public ResponseEntity<TroubleTicketDto> createTroubleTicket(TroubleTicketCreateRequestDto request) {
        AuthenticatedClient client = currentClient.get();
        // The schema allows only status "new", so the status field carries no further information.
        NewTroubleTicket newTicket = new NewTroubleTicket(
                request.externalId(), request.serviceId(), request.description(), request.note());
        return switch (service.create(client.tenantId(), client.subject(), newTicket)) {
            case Created created -> ResponseEntity
                    .created(ApiPaths.troubleTicket(created.ticket().id()))
                    .body(TroubleTicketDtoMapper.toDto(created.ticket()));
            case AlreadyExists existing -> ResponseEntity.ok()
                    .location(ApiPaths.troubleTicket(existing.ticket().id()))
                    .body(TroubleTicketDtoMapper.toDto(existing.ticket()));
        };
    }

    /**
     * Query parameters are ignored: the contract v1 has no paging or filtering and no 400 for this operation.
     */
    @Override
    public ResponseEntity<List<TroubleTicketSummaryDto>> listTroubleTickets() {
        return ResponseEntity.ok(service.list(currentClient.get().tenantId()).stream()
                .map(TroubleTicketDtoMapper::toDto)
                .toList());
    }

    @Override
    public ResponseEntity<TroubleTicketDto> getTroubleTicketById(String id) {
        TroubleTicketView ticket = service.get(currentClient.get().tenantId(), id);
        return ResponseEntity.ok(TroubleTicketDtoMapper.toDto(ticket));
    }

    /**
     * The schema allows only status {@code closed} in the request, so the body carries no further information.
     */
    @Override
    public ResponseEntity<TroubleTicketDto> closeTroubleTicket(String id,
            TroubleTicketCloseStatusRequestDto request) {
        AuthenticatedClient client = currentClient.get();
        TroubleTicketView ticket = service.close(client.tenantId(), client.subject(), id);
        return ResponseEntity.ok(TroubleTicketDtoMapper.toDto(ticket));
    }
}
