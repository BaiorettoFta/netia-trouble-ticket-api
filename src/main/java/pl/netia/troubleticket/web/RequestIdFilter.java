package pl.netia.troubleticket.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id: reuses a safe {@code X-Request-Id} sent by the client or a gateway,
 * otherwise generates one. The id is returned in the response header, put into the logging MDC and used as
 * {@code requestId} in error bodies. Runs before Spring Security so that 401 and 403 responses carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "requestId";

    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final HexFormat HEX = HexFormat.of();

    /**
     * Returns the id assigned to the request by this filter, or a fresh one if the filter did not run.
     */
    public static String requestIdOf(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String requestId ? requestId : newRequestId();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getAttribute(ATTRIBUTE) instanceof String assigned
                ? assigned
                : acceptOrGenerate(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Error dispatches (the /error page) are filtered too, so their logs keep the same request id.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private static String acceptOrGenerate(String incoming) {
        return incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches() ? incoming : newRequestId();
    }

    private static String newRequestId() {
        return "req-" + HEX.toHexDigits(ThreadLocalRandom.current().nextLong());
    }
}
