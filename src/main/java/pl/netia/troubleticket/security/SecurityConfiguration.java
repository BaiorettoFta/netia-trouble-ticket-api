package pl.netia.troubleticket.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import pl.netia.troubleticket.web.ApiPaths;
import pl.netia.troubleticket.web.ErrorResponseWriter;

/**
 * Every endpoint requires a Bearer JWT. Spring Boot verifies the signature (JWKS), issuer, audience and time window
 * from {@code spring.security.oauth2.resourceserver.jwt.*}; the {@link TokenClaimsValidator} bean is added to those
 * checks automatically. The API is stateless and used by systems, so sessions, CSRF, form login and HTTP Basic are off.
 *
 * <p>Permissions are OAuth2 scopes: {@code trouble-ticket:read} for reading, {@code trouble-ticket:write} for
 * creating, closing and adding notes. They are checked here, by method and path, before the request body is read
 * or a ticket is looked up, so a caller without permission always gets 403 rather than 400 or 404.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String READ = "SCOPE_trouble-ticket:read";
    static final String WRITE = "SCOPE_trouble-ticket:write";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ErrorResponseWriter errorResponseWriter)
            throws Exception {
        AuthenticationEntryPoint entryPoint = new ErrorBodyAuthenticationEntryPoint(errorResponseWriter);
        AccessDeniedHandler accessDeniedHandler = new ErrorBodyAccessDeniedHandler(errorResponseWriter);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.TROUBLE_TICKETS, ApiPaths.TROUBLE_TICKET)
                        .hasAuthority(READ)
                        .requestMatchers(HttpMethod.POST, ApiPaths.TROUBLE_TICKETS, ApiPaths.TROUBLE_TICKET_NOTES)
                        .hasAuthority(WRITE)
                        .requestMatchers(HttpMethod.PATCH, ApiPaths.TROUBLE_TICKET)
                        .hasAuthority(WRITE)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    OAuth2TokenValidator<Jwt> tokenClaimsValidator() {
        return new TokenClaimsValidator();
    }
}
