package ai.generalcontracts.startup.config;

import ai.generalcontracts.startup.config.filters.ApplicationX509AuthenticationFilter;
import ai.generalcontracts.startup.config.filters.CipherValidationFilter;
import ai.generalcontracts.startup.observability.SecurityAuditLogger;
import ai.generalcontracts.startup.security.ClientCertificateFilter;
import ai.generalcontracts.startup.util.JWTDecode;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import static ai.generalcontracts.startup.util.JWTDecode.jwtValidators;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfiguration {

    private static final String CSP =
            "default-src 'none'; base-uri 'none'; frame-ancestors 'none'";

    private final JWTDecode jwtDecode;
    private final ConfigProperties configProperties;
    private final ClientConfiguration.WrapperTrustManager wrapperTrustManager;

    public SecurityConfiguration(
            JWTDecode jwtDecode,
            ConfigProperties configProperties,
            @Nullable ClientConfiguration.WrapperTrustManager wrapperTrustManager
    ) {
        this.jwtDecode = jwtDecode;
        this.configProperties = configProperties;
        this.wrapperTrustManager = wrapperTrustManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityAuditLogger auditLogger
    ) throws Exception {
        AuthenticationEntryPoint safeEntryPoint = (request, response, exception) -> {
            auditLogger.authenticationDenied(request);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
        };
        AccessDeniedHandler safeDeniedHandler = (request, response, exception) -> {
            auditLogger.authorizationDenied(request);
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"forbidden\"}");
        };

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").access(jwtDecode.audited("SCOPE_OPS_READ", auditLogger))
                        .requestMatchers(HttpMethod.GET, "/orders").access(jwtDecode.audited("SCOPE_ORDER_READ", auditLogger))
                        .requestMatchers(HttpMethod.POST, "/orders").access(jwtDecode.audited("SCOPE_ORDER_WRITE", auditLogger))
                        .requestMatchers(HttpMethod.GET, "/api/chain/status").access(jwtDecode.audited("SCOPE_CHAIN_STATUS_READ", auditLogger))
                        .requestMatchers(HttpMethod.POST, "/api/transfers").access(SecurityConfiguration::isJwtPrincipal)
                        .requestMatchers(HttpMethod.GET, "/api/transfers/*").access(SecurityConfiguration::isJwtPrincipal)
                        .requestMatchers("/error").denyAll()
                        .anyRequest().denyAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(safeEntryPoint)
                        .accessDeniedHandler(safeDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(safeEntryPoint)
                        .accessDeniedHandler(safeDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtDecode.jwtAuthenticationConverter())))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy",
                                "camera=(), microphone=(), geolocation=()"
                        )));

        http.addFilterBefore(new CipherValidationFilter(), BearerTokenAuthenticationFilter.class);
        http.x509(x509 -> x509
                .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
                .x509AuthenticationFilter(new ApplicationX509AuthenticationFilter(wrapperTrustManager, configProperties.getSsl().isSslBundleEnabled()))
        );
        http.addFilterAfter(
                new ClientCertificateFilter(auditLogger),
                BearerTokenAuthenticationFilter.class
        );
        return http.build();
    }

    private static AuthorizationDecision isJwtPrincipal(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context
    ) {
        Authentication current = authentication.get();
        return new AuthorizationDecision(current instanceof JwtAuthenticationToken && current.isAuthenticated());
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.security.jwt.issuer:}") String issuer,
            @Value("${app.security.jwt.audience:}") String audience
    ) {
        if (issuer.isBlank() || audience.isBlank()) {
            return token -> {
                throw new BadJwtException("JWT validation is not configured");
            };
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        decoder.setJwtValidator(jwtValidators(issuer, audience));
        return decoder;
    }
}
