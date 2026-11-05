package pl.netia.troubleticket.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Body of the {@code /error} page, used for failures outside Spring MVC (for example an exception thrown in a
 * servlet filter or a request rejected by the servlet container). It follows the contract {@code Error} schema
 * instead of Spring Boot's default attributes, which would expose the path and the exception.
 */
@Component
class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Object status = webRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE, RequestAttributes.SCOPE_REQUEST);
        ErrorCode code = ErrorCode.forStatus(HttpStatusCode.valueOf(status instanceof Integer value ? value : 500));
        HttpServletRequest request = ((ServletWebRequest) webRequest).getRequest();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("code", code.name());
        attributes.put("message", code.defaultMessage());
        attributes.put("requestId", RequestIdFilter.requestIdOf(request));
        return attributes;
    }
}
