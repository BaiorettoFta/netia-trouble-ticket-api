package pl.netia.troubleticket.support;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Checks every HTTP response a test client receives against the OpenAPI contract: documented status code, content
 * type, headers and body schema (including additionalProperties: false). Request violations are ignored on purpose,
 * because many tests send invalid requests deliberately; responses must match the contract in every case.
 */
public final class ContractValidator implements ClientHttpRequestInterceptor {

    public static final Path CONTRACT = Path.of("api", "trouble-ticket-api.yaml");

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(CONTRACT.toAbsolutePath().toUri().toString())
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    /**
     * Messages describing how the response breaks the contract; empty when it complies.
     */
    public static String violations(Request request, Response response) {
        ValidationReport report = VALIDATOR.validate(request, response);
        return report.hasErrors() ? SimpleValidationReportFormat.getInstance().apply(report) : "";
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] requestBody, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, requestBody);
        byte[] responseBody = response.getBody().readAllBytes();

        SimpleRequest.Builder validatedRequest =
                new SimpleRequest.Builder(request.getMethod().name(), request.getURI().getRawPath());
        request.getHeaders().forEach(validatedRequest::withHeader);
        if (requestBody.length > 0) {
            validatedRequest.withBody(requestBody);
        }
        SimpleResponse.Builder validatedResponse = SimpleResponse.Builder.status(response.getStatusCode().value());
        response.getHeaders().forEach(validatedResponse::withHeader);
        if (responseBody.length > 0) {
            validatedResponse.withBody(responseBody);
        }

        String violations = violations(validatedRequest.build(), validatedResponse.build());
        if (!violations.isEmpty()) {
            throw new AssertionError("Response does not match the contract: " + request.getMethod() + " "
                    + request.getURI().getRawPath() + " -> " + response.getStatusCode().value() + "\n" + violations);
        }
        return new BufferedResponse(response, responseBody);
    }

    /**
     * The response body was consumed for validation; this hands the same bytes to the test.
     */
    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
