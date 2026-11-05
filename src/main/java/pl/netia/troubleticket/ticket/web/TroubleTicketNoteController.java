package pl.netia.troubleticket.ticket.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.netia.troubleticket.openapi.api.TroubleTicketNoteApi;
import pl.netia.troubleticket.openapi.model.NoteCreateRequestDto;
import pl.netia.troubleticket.openapi.model.NoteDto;
import pl.netia.troubleticket.security.AuthenticatedClient;
import pl.netia.troubleticket.security.CurrentClient;
import pl.netia.troubleticket.ticket.application.NoteView;
import pl.netia.troubleticket.ticket.application.TroubleTicketService;
import pl.netia.troubleticket.ticket.domain.NoteText;

/**
 * Implements the interface generated from the contract for the note subresource.
 */
@RestController
class TroubleTicketNoteController implements TroubleTicketNoteApi {

    private final TroubleTicketService service;
    private final CurrentClient currentClient;

    TroubleTicketNoteController(TroubleTicketService service, CurrentClient currentClient) {
        this.service = service;
        this.currentClient = currentClient;
    }

    /**
     * 201 without a {@code Location} header: the contract defines neither the header nor an address of a single
     * note.
     */
    @Override
    public ResponseEntity<NoteDto> addTroubleTicketNote(String id, NoteCreateRequestDto request) {
        AuthenticatedClient client = currentClient.get();
        NoteView note = service.addNote(client.tenantId(), client.subject(), id, new NoteText(request.text()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TroubleTicketDtoMapper.toDto(note));
    }
}
