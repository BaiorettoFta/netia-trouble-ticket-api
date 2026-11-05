package pl.netia.troubleticket.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import pl.netia.troubleticket.ticket.domain.InputViolation.Reason;

class NewTroubleTicketTest {

    /** NUL and a lone surrogate, built from code points to keep the source file plain ASCII. */
    private static final String NUL = String.valueOf((char) 0);
    private static final String LONE_SURROGATE = String.valueOf((char) 0xD800);
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @Test
    void acceptsExternalIdOfExactly255Characters() {
        // 254 letters and one emoji: 255 characters for PostgreSQL, 256 UTF-16 units for Java.
        String externalId = "a".repeat(254) + EMOJI;

        assertThatNoException().isThrownBy(() -> ticket(externalId, "Opis", "Notatka"));
    }

    @Test
    void rejectsExternalIdLongerThan255Characters() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> ticket("a".repeat(256), "Opis", "Notatka"))
                .satisfies(exception -> assertThat(exception.violations())
                        .containsExactly(new InputViolation("externalId", Reason.TOO_LONG, 255)));
    }

    @Test
    void rejectsTextThatPostgresqlCannotStore() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> ticket("OK-1", "Opis" + NUL, "Notatka"))
                .satisfies(exception -> assertThat(exception.violations())
                        .containsExactly(new InputViolation("description", Reason.FORBIDDEN_CHARACTERS, 0)));
    }

    @Test
    void rejectsLoneSurrogateThatCannotBeEncodedInUtf8() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> ticket("OK-1", "Opis", "Notatka " + LONE_SURROGATE))
                .satisfies(exception -> assertThat(exception.violations())
                        .containsExactly(new InputViolation("note", Reason.FORBIDDEN_CHARACTERS, 0)));
    }

    @Test
    void acceptsEmojiAndOtherCompleteCharacters() {
        assertThatNoException().isThrownBy(() -> ticket("OK-" + EMOJI, "Usługa nie działa " + EMOJI, "Zażółć gęślą"));
    }

    @Test
    void acceptsTextMadeOfWhitespaceBecauseTheContractAllowsIt() {
        assertThatNoException().isThrownBy(() -> ticket(" ", "   ", "\t"));
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> ticket("a".repeat(300) + NUL, "Opis" + NUL, "Notatka" + NUL))
                .satisfies(exception -> assertThat(exception.violations()).containsExactly(
                        new InputViolation("externalId", Reason.TOO_LONG, 255),
                        new InputViolation("externalId", Reason.FORBIDDEN_CHARACTERS, 0),
                        new InputViolation("description", Reason.FORBIDDEN_CHARACTERS, 0),
                        new InputViolation("note", Reason.FORBIDDEN_CHARACTERS, 0)));
    }

    private static NewTroubleTicket ticket(String externalId, String description, String note) {
        return new NewTroubleTicket(externalId, 987654321L, description, note);
    }
}
