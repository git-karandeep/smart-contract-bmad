package ai.generalcontracts.startup.util;

import ai.generalcontracts.startup.observability.SecurityAuditLogger;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class JWTDecode {
    public static OAuth2TokenValidator<Jwt> jwtValidators(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required audience is missing", null));
        OAuth2TokenValidator<Jwt> subjectValidator = jwt -> StringUtils.hasText(jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required subject is missing", null));
        OAuth2TokenValidator<Jwt> expirationValidator = jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required expiration is missing", null));
        OAuth2TokenValidator<Jwt> tokenTypeValidator = jwt -> {
            Object tokenType = jwt.getHeaders().get("typ");
            return "at+jwt".equals(tokenType) || "JWT".equals(tokenType)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Unsupported JWT type", null));
        };
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                audienceValidator,
                subjectValidator,
                expirationValidator,
                tokenTypeValidator
        );
    }

    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    public AuthorizationManager<RequestAuthorizationContext> audited(
            String requiredAuthority,
            SecurityAuditLogger auditLogger
    ) {
        return (authentication, context) -> {
            Authentication caller = authentication.get();
            boolean allowed = caller != null && caller.isAuthenticated()
                    && caller.getAuthorities().stream()
                    .anyMatch(authority -> requiredAuthority.equals(authority.getAuthority()));
            auditLogger.authorizationDecision(
                    context.getRequest(),
                    allowed,
                    requiredAuthority
            );
            return new AuthorizationDecision(allowed);
        };
    }}