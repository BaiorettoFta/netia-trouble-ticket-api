package pl.netia.troubleticket.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.netia.troubleticket.ticket.domain.InputViolation.Reason;

class NoteTextTest {

    /** NUL and a lone surrogate, built from code points to keep the source file free of control characters. */
    private static final String NUL = String.valueOf((char) 0);
    private static final String LONE_SURROGATE = String.valueOf((char) 0xDC00);
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"Klient prosi o potwierdzenie terminu.", "Zażółć gęślą jaźń", " ", "\t\n"})
    void acceptsAnyTextTheContractAllowsIncludingWhitespaceOnly(String text) {
        assertThatNoException().isThrownBy(() -> new NoteText(text));
    }

    @Test
    void acceptsCharactersOutsideTheBasicMultilingualPlane() {
        assertThat(new NoteText("Usługa działa " + EMOJI).value()).endsWith(EMOJI);
    }

    @Test
    void rejectsTheNulCharacterThatPostgresqlCannotStore() {
        assertRejected("Notatka" + NUL);
    }

    @Test
    void rejectsALoneSurrogateThatCannotBeEncodedInUtf8() {
        assertRejected("Notatka " + LONE_SURROGATE);
    }

    private static void assertRejected(String text) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> new NoteText(text))
                .satisfies(exception -> assertThat(exception.violations())
                        .containsExactly(new InputViolation("text", Reason.FORBIDDEN_CHARACTERS, 0)));
    }
}
