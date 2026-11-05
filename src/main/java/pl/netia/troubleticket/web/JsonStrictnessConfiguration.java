package pl.netia.troubleticket.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Part of the strict JSON settings that cannot be expressed as {@code spring.jackson.*} properties (see
 * application.yaml for the rest). Text fields in the contract must be JSON strings: without this rule Jackson
 * turns {@code 123} or {@code true} into {@code "123"} and {@code "true"}.
 */
@Configuration(proxyBeanMethods = false)
public class JsonStrictnessConfiguration {

    @Bean
    JsonMapperBuilderCustomizer rejectNumbersAndBooleansForTextFields() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
