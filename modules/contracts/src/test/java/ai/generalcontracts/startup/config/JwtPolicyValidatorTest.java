package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThat;
import static ai.generalcontracts.startup.util.JWTDecode.jwtValidators;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPolicyValidatorTest {
    private static final String ISSUER = "https://issuer.example.invalid/";
    private static final String AUDIENCE = "contracts-api";

    @Test
    void acceptsOnlyTokensWithTheConfiguredIssuerAudienceAndValidTimeWindow() {
        Instant now = Instant.now();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(30), now.plusSeconds(300), null))
            .getErrors()).isEmpty();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(30), now.plusSeconds(300), null, "JWT"))
            .hasErrors()).isFalse();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(30), now.plusSeconds(300), null, null))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(30), now.plusSeconds(300), null, "id+jwt"))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE), now.minusSeconds(30), null, null))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token("https://attacker.example.invalid/", List.of(AUDIENCE),
                now.minusSeconds(30), now.plusSeconds(300), null))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of("other-api"),
                now.minusSeconds(30), now.plusSeconds(300), null))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE),
                now.minusSeconds(600), now.minusSeconds(300), null))
            .hasErrors()).isTrue();

        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(token(ISSUER, List.of(AUDIENCE),
                now.minusSeconds(30), now.plusSeconds(300), now.plusSeconds(300)))
            .hasErrors()).isTrue();

        Jwt missingSubject = Jwt.withTokenValue("synthetic-test-token")
            .header("alg", "RS256")
            .header("typ", "at+jwt")
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .issuedAt(now.minusSeconds(30))
            .expiresAt(now.plusSeconds(300))
            .build();
        assertThat(jwtValidators(ISSUER, AUDIENCE)
            .validate(missingSubject)
            .hasErrors()).isTrue();
    }

    private Jwt token(
        String issuer,
        List<String> audiences,
        Instant issuedAt,
        Instant expiresAt,
        Instant notBefore
    ) {
        return token(issuer, audiences, issuedAt, expiresAt, notBefore, "at+jwt");
    }

    private Jwt token(
        String issuer,
        List<String> audiences,
        Instant issuedAt,
        Instant expiresAt,
        Instant notBefore,
        String tokenType
    ) {
        Jwt.Builder builder = Jwt.withTokenValue("synthetic-test-token")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(audiences)
            .subject("test-principal")
            .issuedAt(issuedAt);
        if (expiresAt != null) {
            builder.expiresAt(expiresAt);
        }
        if (tokenType != null) {
            builder.header("typ", tokenType);
        }
        if (notBefore != null) {
            builder.notBefore(notBefore);
        }
        return builder.build();
    }
}
