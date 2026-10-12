package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.generalcontracts.startup.transfer.ScreeningRequestRecord;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ScreeningResultValidatorTest {
    private final ScreeningResultValidator validator = new ScreeningResultValidator();
    private final ScreeningRequestRecord expected = new ScreeningRequestRecord(
        "request-1", "transfer-1:KYC", "transfer-1", "provider-1", "KYC", "v1", Instant.now()
    );

    @Test
    void acceptsOnlyCorrelatedProviderDecision() {
        assertDoesNotThrow(() -> validator.validate(result(), expected));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
            new ScreeningResult("provider-1", "message-1", "other-transfer", "request-1",
                "transfer-1:KYC", ScreeningType.KYC, ScreeningDecision.APPROVED, Instant.now()),
            expected
        ));
    }

    @Test
    void hmacAuthenticationFailsClosedWithoutProviderSecret() {
        HmacScreeningResultAuthenticator authenticator = new HmacScreeningResultAuthenticator(providerId -> null);
        assertFalse(authenticator.isAuthenticated(result(), Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void authenticatesCanonicalMessageUsingConstantTimeDigestComparison() throws Exception {
        byte[] secret = "test-provider-key".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HmacScreeningResultAuthenticator authenticator =
            new HmacScreeningResultAuthenticator(providerId -> secret);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
            mac.doFinal(result().canonicalMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        assertTrue(authenticator.isAuthenticated(result(), signature));
        assertFalse(authenticator.isAuthenticated(result(), Base64.getEncoder().encodeToString(new byte[32])));
    }

    private ScreeningResult result() {
        return new ScreeningResult(
            "provider-1", "message-1", "transfer-1", "request-1", "transfer-1:KYC",
            ScreeningType.KYC, ScreeningDecision.APPROVED, Instant.parse("2026-10-11T20:00:00Z")
        );
    }
}
