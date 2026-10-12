package ai.generalcontracts.startup.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacScreeningResultAuthenticator implements ScreeningResultAuthenticator {
    private final ProviderSecretResolver secretResolver;

    public HmacScreeningResultAuthenticator(ProviderSecretResolver secretResolver) {
        this.secretResolver = secretResolver;
    }

    @Override
    public boolean isAuthenticated(ScreeningResult result, String signature) {
        if (result == null || result.providerId() == null || result.providerId().isBlank()
            || signature == null || signature.isBlank()) {
            return false;
        }
        byte[] secret;
        try {
            secret = secretResolver.resolveSecret(result.providerId());
        } catch (RuntimeException unavailable) {
            return false;
        }
        if (secret == null || secret.length == 0) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(result.canonicalMessage().getBytes(StandardCharsets.UTF_8));
            byte[] supplied = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            return false;
        }
    }
}
