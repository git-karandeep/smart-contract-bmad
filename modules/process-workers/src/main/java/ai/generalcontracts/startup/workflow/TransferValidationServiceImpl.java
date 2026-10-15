package ai.generalcontracts.startup.workflow;

import ai.generalcontracts.startup.transfer.NetworkConfiguration;
import ai.generalcontracts.startup.transfer.NetworkConfigurationRepository;
import ai.generalcontracts.startup.transfer.TokenConfiguration;
import ai.generalcontracts.startup.transfer.TokenConfigurationRepository;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.WalletEligibility;
import ai.generalcontracts.startup.transfer.WalletEligibilityRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransferValidationServiceImpl implements TransferValidationPolicy {
    private final NetworkConfigurationRepository networkConfigRepository;
    private final TokenConfigurationRepository tokenConfigRepository;
    private final WalletEligibilityRepository walletEligibilityRepository;

    public TransferValidationServiceImpl(
        NetworkConfigurationRepository networkConfigRepository,
        TokenConfigurationRepository tokenConfigRepository,
        WalletEligibilityRepository walletEligibilityRepository
    ) {
        this.networkConfigRepository = networkConfigRepository;
        this.tokenConfigRepository = tokenConfigRepository;
        this.walletEligibilityRepository = walletEligibilityRepository;
    }

    @Override
    public ValidationDecision validate(TransferInstruction instruction) {
        String sourceWallet = instruction.getSourceWallet();
        String destinationWallet = instruction.getDestinationWallet();
        long networkId = instruction.getNetworkId();
        String tokenIdentifier = instruction.getTokenIdentifier();
        String tenantId = instruction.getTenantId();

        Optional<NetworkConfiguration> networkConfig = networkConfigRepository.findByNetworkId(networkId);
        if (networkConfig.isEmpty()) {
            return ValidationDecision.rejected("NETWORK_NOT_CONFIGURED");
        }
        if (!networkConfig.get().isEnabled()) {
            return ValidationDecision.rejected("NETWORK_DISABLED");
        }

        Optional<TokenConfiguration> tokenConfig = tokenConfigRepository.findByNetworkIdAndTokenIdentifier(
            networkId, tokenIdentifier
        );
        if (tokenConfig.isEmpty()) {
            return ValidationDecision.rejected("TOKEN_NOT_CONFIGURED");
        }
        if (!tokenConfig.get().isEnabled()) {
            return ValidationDecision.rejected("TOKEN_DISABLED");
        }

        if (!isValidAddressFormat(sourceWallet, networkConfig.get().getAddressFormat())) {
            return ValidationDecision.rejected("INVALID_SOURCE_ADDRESS_FORMAT");
        }
        if (!isValidAddressFormat(destinationWallet, networkConfig.get().getAddressFormat())) {
            return ValidationDecision.rejected("INVALID_DESTINATION_ADDRESS_FORMAT");
        }

        if (sourceWallet.equalsIgnoreCase(destinationWallet) && !tokenConfig.get().isAllowSelfTransfer()) {
            return ValidationDecision.rejected("SELF_TRANSFER_NOT_ALLOWED");
        }

        Optional<WalletEligibility> sourceEligibility = walletEligibilityRepository
            .findByTenantIdAndWalletAddress(tenantId, sourceWallet);
        if (sourceEligibility.isEmpty()) {
            return ValidationDecision.rejected("SOURCE_WALLET_NOT_REGISTERED");
        }
        if (!sourceEligibility.get().isEligible()) {
            return ValidationDecision.rejected("SOURCE_WALLET_INELIGIBLE");
        }
        if (sourceEligibility.get().isFrozen()) {
            return ValidationDecision.rejected("SOURCE_WALLET_FROZEN");
        }

        Optional<WalletEligibility> destEligibility = walletEligibilityRepository
            .findByTenantIdAndWalletAddress(tenantId, destinationWallet);
        if (destEligibility.isEmpty()) {
            return ValidationDecision.rejected("DESTINATION_WALLET_NOT_REGISTERED");
        }
        if (!destEligibility.get().isEligible()) {
            return ValidationDecision.rejected("DESTINATION_WALLET_INELIGIBLE");
        }
        if (destEligibility.get().isFrozen()) {
            return ValidationDecision.rejected("DESTINATION_WALLET_FROZEN");
        }

        String amount = instruction.getAmount();
        try {
            BigDecimal transferAmount = new BigDecimal(amount);
            if (tokenConfig.get().getMinTransferAmount() != null) {
                BigDecimal minAmount = new BigDecimal(tokenConfig.get().getMinTransferAmount());
                if (transferAmount.compareTo(minAmount) < 0) {
                    return ValidationDecision.rejected("AMOUNT_BELOW_MINIMUM");
                }
            }
            if (tokenConfig.get().getMaxTransferAmount() != null) {
                BigDecimal maxAmount = new BigDecimal(tokenConfig.get().getMaxTransferAmount());
                if (transferAmount.compareTo(maxAmount) > 0) {
                    return ValidationDecision.rejected("AMOUNT_ABOVE_MAXIMUM");
                }
            }
        } catch (NumberFormatException e) {
            return ValidationDecision.rejected("INVALID_AMOUNT_FORMAT");
        }

        return new ValidationDecision(true, null);
    }

    private boolean isValidAddressFormat(String address, String format) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return switch (format.toLowerCase()) {
            case "ethereum", "evm" -> isValidEvmAddress(address);
            case "bitcoin" -> isValidBitcoinAddress(address);
            default -> address.length() >= 26 && address.length() <= 64;
        };
    }

    private boolean isValidEvmAddress(String address) {
        return address.matches("(?i)^0x[0-9a-f]{40}$");
    }

    private boolean isValidBitcoinAddress(String address) {
        return address.matches("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")
            || address.matches("^(bc1q|bc1p)[a-zA-Z0-9]{39,59}$");
    }
}
