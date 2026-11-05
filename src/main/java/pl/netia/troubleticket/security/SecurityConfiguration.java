package pl.netia.troubleticket.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import pl.netia.troubleticket.web.ErrorResponseWriter;

/**
 * Every endpoint requires a Bearer JWT. Spring Boot verifies the signature (JWKS), issuer, audience and time window
 * from {@code spring.security.oauth2.resourceserver.jwt.*}; the {@link TokenClaimsValidator} bean is added to those
 * checks automatically. The API is stateless and used by systems, so sessions, CSRF, form login and HTTP Basic are off.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ErrorResponseWriter errorResponseWriter)
            throws Exception {
        AuthenticationEntryPoint entryPoint = new ErrorBodyAuthenticationEntryPoint(errorResponseWriter);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    OAuth2TokenValidator<Jwt> tokenClaimsValidator() {
        return new TokenClaimsValidator();
    }
}
