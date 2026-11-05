package pl.netia.troubleticket.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenClientSendsNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("req-[0-9a-f]{16}");
        assertThat(RequestIdFilter.requestIdOf(request)).isEqualTo(response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void reusesSafeRequestIdSentByClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "gateway-42.abc_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("gateway-42.abc_DEF");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "with space",
            "line\nbreak",
            "quote\"",
            "01234567890123456789012345678901234567890123456789012345678901234" // 65 characters
    })
    void replacesUnsafeRequestId(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, unsafe);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("req-[0-9a-f]{16}");
    }

    @Test
    void exposesRequestIdToLogsOnlyWhileRequestIsProcessed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "req-1");
        AtomicReference<String> seenByApplication = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seenByApplication.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(seenByApplication).hasValue("req-1");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
