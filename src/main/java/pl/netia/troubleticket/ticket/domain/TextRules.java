package pl.netia.troubleticket.ticket.domain;

import java.util.List;

/**
 * Text rules that the contract schema cannot express but the storage requires. Lengths are counted in Unicode code
 * points, the way PostgreSQL counts characters.
 */
final class TextRules {

    private TextRules() {
    }

    /**
     * PostgreSQL cannot store the NUL character in text, and a lone surrogate (half of a UTF-16 pair) cannot be
     * encoded in UTF-8. Without this check the first would end in a 500 and the second in silently altered text.
     */
    static void checkStorable(String field, String value, List<InputViolation> violations) {
        boolean unstorable = value.codePoints().anyMatch(codePoint -> codePoint == 0
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
        if (unstorable) {
            violations.add(InputViolation.forbiddenCharacters(field));
        }
    }

    static void checkMaxLength(String field, String value, int maxLength, List<InputViolation> violations) {
        if (value.codePointCount(0, value.length()) > maxLength) {
            violations.add(InputViolation.tooLong(field, maxLength));
        }
    }
}
