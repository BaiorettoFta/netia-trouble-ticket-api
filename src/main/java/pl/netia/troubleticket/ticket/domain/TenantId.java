package pl.netia.troubleticket.ticket.domain;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Tenant scope of a request. It comes only from a verified access token, never from the request body.
 */
public record TenantId(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public TenantId {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Tenant id must match " + FORMAT.pattern());
        }
    }

    public static boolean isValid(@Nullable String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
