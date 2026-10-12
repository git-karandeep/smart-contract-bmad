package ai.generalcontracts.startup.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
    @NotBlank @Pattern(regexp = "(?i)^0x[0-9a-f]{40}$") String sourceWallet,
    @NotBlank @Pattern(regexp = "(?i)^0x[0-9a-f]{40}$") String destinationWallet,
    @NotBlank String tokenIdentifier,
    @NotBlank @Pattern(regexp = "^(?:0|[1-9][0-9]{0,35})(?:\\.[0-9]{1,18})?$") String amount,
    @NotNull @Positive Long networkId,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String idempotencyKey
) {
}
