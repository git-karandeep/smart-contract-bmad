package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.TransferInstruction;
import java.math.BigDecimal;
import java.util.regex.Pattern;

public interface TransferValidationPolicy {
    ValidationDecision validate(TransferInstruction instruction);

    static TransferValidationPolicy withEligibilityPolicy(TransferEligibilityPolicy eligibilityPolicy) {
        return instruction -> {
            ValidationDecision structural = validateStructure(instruction);
            if (!structural.valid()) {
                return structural;
            }
            if (eligibilityPolicy == null) {
                return ValidationDecision.rejected("TRANSFER_ELIGIBILITY_POLICY_UNAVAILABLE");
            }

            try {
                ValidationDecision eligibility = eligibilityPolicy.validate(instruction);
                if (eligibility == null) {
                    return ValidationDecision.rejected("TRANSFER_ELIGIBILITY_POLICY_UNAVAILABLE");
                }
                if (eligibility.valid()) {
                    return new ValidationDecision(true, null);
                }
                return ValidationDecision.rejected(
                    hasText(eligibility.reasonCode())
                        ? eligibility.reasonCode()
                        : "TRANSFER_ELIGIBILITY_POLICY_REJECTED"
                );
            } catch (RuntimeException failure) {
                return ValidationDecision.rejected("TRANSFER_ELIGIBILITY_POLICY_FAILED");
            }
        };
    }

    private static ValidationDecision validateStructure(TransferInstruction instruction) {
        if (instruction == null) {
            return ValidationDecision.rejected("TRANSFER_INSTRUCTION_MISSING");
        }
        if (!isEvmAddress(instruction.getSourceWallet())) {
            return ValidationDecision.rejected("TRANSFER_SOURCE_WALLET_INVALID");
        }
        if (!isEvmAddress(instruction.getDestinationWallet())) {
            return ValidationDecision.rejected("TRANSFER_DESTINATION_WALLET_INVALID");
        }
        if (instruction.getSourceWallet().equalsIgnoreCase(instruction.getDestinationWallet())) {
            return ValidationDecision.rejected("TRANSFER_SOURCE_DESTINATION_IDENTICAL");
        }
        String tokenIdentifier = instruction.getTokenIdentifier();
        if (!hasText(tokenIdentifier) || !tokenIdentifier.equals(tokenIdentifier.trim())
            || (startsWithHexPrefix(tokenIdentifier) && !isEvmAddress(tokenIdentifier))) {
            return ValidationDecision.rejected("TRANSFER_TOKEN_IDENTIFIER_INVALID");
        }
        if (instruction.getNetworkId() <= 0) {
            return ValidationDecision.rejected("TRANSFER_NETWORK_ID_INVALID");
        }
        String amount = instruction.getAmount();
        if (amount == null || !TransferValidationPatterns.EXACT_DECIMAL_AMOUNT.matcher(amount).matches()) {
            return ValidationDecision.rejected("TRANSFER_AMOUNT_INVALID");
        }
        try {
            if (new BigDecimal(amount).signum() <= 0) {
                return ValidationDecision.rejected("TRANSFER_AMOUNT_INVALID");
            }
        } catch (NumberFormatException invalidAmount) {
            return ValidationDecision.rejected("TRANSFER_AMOUNT_INVALID");
        }
        return new ValidationDecision(true, null);
    }

    private static boolean isEvmAddress(String value) {
        return value != null && TransferValidationPatterns.EVM_ADDRESS.matcher(value).matches();
    }

    private static boolean startsWithHexPrefix(String value) {
        return value.regionMatches(true, 0, "0x", 0, 2);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record ValidationDecision(boolean valid, String reasonCode) {
        public static ValidationDecision rejected(String reasonCode) {
            return new ValidationDecision(false, reasonCode);
        }
    }

}

final class TransferValidationPatterns {
    static final Pattern EVM_ADDRESS = Pattern.compile("(?i)^0x[0-9a-f]{40}$");
    static final Pattern EXACT_DECIMAL_AMOUNT =
        Pattern.compile("^(?:0|[1-9][0-9]{0,35})(?:\\.[0-9]{1,18})?$");

    private TransferValidationPatterns() {
    }
}
