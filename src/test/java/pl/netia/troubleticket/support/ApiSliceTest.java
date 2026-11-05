package pl.netia.troubleticket.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import pl.netia.troubleticket.security.SecurityConfiguration;
import pl.netia.troubleticket.web.ErrorResponseWriter;
import pl.netia.troubleticket.web.JsonStrictnessConfiguration;

/**
 * Web slice test with the real security and web configuration, and a {@link FakeJwtDecoder} instead of a real
 * identity provider.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import({SecurityConfiguration.class, ErrorResponseWriter.class, JsonStrictnessConfiguration.class,
        FakeJwtDecoder.class})
public @interface ApiSliceTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] controllers() default {};
}
