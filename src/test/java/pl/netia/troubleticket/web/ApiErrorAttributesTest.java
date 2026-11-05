package pl.netia.troubleticket.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ApiErrorAttributesTest {

    private final ApiErrorAttributes errorAttributes = new ApiErrorAttributes();

    @Test
    void rendersErrorPageInContractFormatOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalArgumentException("internal detail"));

        Map<String, Object> body = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(body)
                .containsOnlyKeys("code", "message", "requestId")
                .containsEntry("code", "VALIDATION_ERROR")
                .containsEntry("message", "Żądanie jest niepoprawne.");
        assertThat((String) body.get("requestId")).matches("req-[0-9a-f]{16}");
    }

    @Test
    void treatsMissingStatusAsInternalError() {
        Map<String, Object> body = errorAttributes.getErrorAttributes(
                new ServletWebRequest(new MockHttpServletRequest()), ErrorAttributeOptions.defaults());

        assertThat(body).containsEntry("code", "INTERNAL_ERROR");
    }
}
