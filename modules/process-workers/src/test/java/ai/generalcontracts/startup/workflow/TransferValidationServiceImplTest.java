package ai.generalcontracts.startup.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.generalcontracts.startup.transfer.NetworkConfiguration;
import ai.generalcontracts.startup.transfer.NetworkConfigurationRepository;
import ai.generalcontracts.startup.transfer.TokenConfiguration;
import ai.generalcontracts.startup.transfer.TokenConfigurationRepository;
import ai.generalcontracts.startup.transfer.TransferInstruction;
import ai.generalcontracts.startup.transfer.WalletEligibility;
import ai.generalcontracts.startup.transfer.WalletEligibilityRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferValidationServiceImplTest {
    private NetworkConfigurationRepository networkConfigRepository;
    private TokenConfigurationRepository tokenConfigRepository;
    private WalletEligibilityRepository walletEligibilityRepository;
    private TransferValidationServiceImpl validator;

    @BeforeEach
    void setUp() {
        networkConfigRepository = mock(NetworkConfigurationRepository.class);
        tokenConfigRepository = mock(TokenConfigurationRepository.class);
        walletEligibilityRepository = mock(WalletEligibilityRepository.class);
        validator = new TransferValidationServiceImpl(
            networkConfigRepository,
            tokenConfigRepository,
            walletEligibilityRepository
        );
    }

    @Test
    void rejectsWhenNetworkNotConfigured() {
        TransferInstruction instruction = createInstruction();
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.empty());

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("NETWORK_NOT_CONFIGURED", decision.reasonCode());
    }

    @Test
    void rejectsWhenNetworkDisabled() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", false, "1");
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("NETWORK_DISABLED", decision.reasonCode());
    }

    @Test
    void rejectsWhenTokenNotConfigured() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.empty());

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("TOKEN_NOT_CONFIGURED", decision.reasonCode());
    }

    @Test
    void rejectsWhenTokenDisabled() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = new TokenConfiguration();
        tokenConfig.setNetworkId(1337);
        tokenConfig.setTokenIdentifier("0xtoken");
        tokenConfig.setEnabled(false);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("TOKEN_DISABLED", decision.reasonCode());
    }

    @Test
    void rejectsInvalidSourceAddressFormat() {
        TransferInstruction instruction = createInstruction();
        instruction.setSourceWallet("invalid-address");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("INVALID_SOURCE_ADDRESS_FORMAT", decision.reasonCode());
    }

    @Test
    void rejectsInvalidDestinationAddressFormat() {
        TransferInstruction instruction = createInstruction();
        instruction.setDestinationWallet("invalid-address");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("INVALID_DESTINATION_ADDRESS_FORMAT", decision.reasonCode());
    }

    @Test
    void rejectsSelfTransferWhenNotAllowed() {
        TransferInstruction instruction = createInstruction();
        instruction.setDestinationWallet("0x1111111111111111111111111111111077777777");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        tokenConfig.setAllowSelfTransfer(false);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("SELF_TRANSFER_NOT_ALLOWED", decision.reasonCode());
    }

    @Test
    void rejectsWhenSourceWalletNotRegistered() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111111111111")).thenReturn(Optional.empty());

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("SOURCE_WALLET_NOT_REGISTERED", decision.reasonCode());
    }

    @Test
    void rejectsWhenSourceWalletIneligible() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", false);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("SOURCE_WALLET_INELIGIBLE", decision.reasonCode());
    }

    @Test
    void rejectsWhenSourceWalletFrozen() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        sourceEligibility.setFrozen(true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("SOURCE_WALLET_FROZEN", decision.reasonCode());
    }

    @Test
    void rejectsWhenDestinationWalletNotRegistered() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x2222222222222222222222222222222222222222")).thenReturn(Optional.empty());

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("DESTINATION_WALLET_NOT_REGISTERED", decision.reasonCode());
    }

    @Test
    void rejectsWhenAmountBelowMinimum() {
        TransferInstruction instruction = createInstruction();
        instruction.setAmount("0.5");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        tokenConfig.setMinTransferAmount("1.0");
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        WalletEligibility destEligibility = new WalletEligibility("tenant-1", 
            "0x2222222222222222222222222222222666666666", true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x2222222222222222222222222222222666666666")).thenReturn(Optional.of(destEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("AMOUNT_BELOW_MINIMUM", decision.reasonCode());
    }

    @Test
    void rejectsWhenAmountAboveMaximum() {
        TransferInstruction instruction = createInstruction();
        instruction.setAmount("10000");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        tokenConfig.setMaxTransferAmount("1000");
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        WalletEligibility destEligibility = new WalletEligibility("tenant-1", 
            "0x2222222222222222222222222222222666666666", true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x2222222222222222222222222222222666666666")).thenReturn(Optional.of(destEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertFalse(decision.valid());
        assertEquals("AMOUNT_ABOVE_MAXIMUM", decision.reasonCode());
    }

    @Test
    void passesValidInstruction() {
        TransferInstruction instruction = createInstruction();
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        WalletEligibility destEligibility = new WalletEligibility("tenant-1", 
            "0x2222222222222222222222222222222666666666", true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x2222222222222222222222222222222666666666")).thenReturn(Optional.of(destEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertTrue(decision.valid());
    }

    @Test
    void allowsSelfTransferWhenConfigured() {
        TransferInstruction instruction = createInstruction();
        instruction.setDestinationWallet("0x1111111111111111111111111111111077777777");
        NetworkConfiguration networkConfig = new NetworkConfiguration(1337, "Test Network", "ethereum", true, "1");
        TokenConfiguration tokenConfig = createTokenConfig();
        tokenConfig.setAllowSelfTransfer(true);
        WalletEligibility sourceEligibility = new WalletEligibility("tenant-1", 
            "0x1111111111111111111111111111111077777777", true);
        when(networkConfigRepository.findByNetworkId(1337)).thenReturn(Optional.of(networkConfig));
        when(tokenConfigRepository.findByNetworkIdAndTokenIdentifier(1337, "0xtoken"))
            .thenReturn(Optional.of(tokenConfig));
        when(walletEligibilityRepository.findByTenantIdAndWalletAddress("tenant-1", 
            "0x1111111111111111111111111111111077777777")).thenReturn(Optional.of(sourceEligibility));

        TransferValidationPolicy.ValidationDecision decision = validator.validate(instruction);

        assertTrue(decision.valid());
    }

    private TransferInstruction createInstruction() {
        TransferInstruction instruction = new TransferInstruction();
        instruction.setInstructionId("transfer-1");
        instruction.setTenantId("tenant-1");
        instruction.setInitiatingPrincipal("user-1");
        instruction.setSourceWallet("0x1111111111111111111111111111111077777777");
        instruction.setDestinationWallet("0x2222222222222222222222222222222666666666");
        instruction.setTokenIdentifier("0xtoken");
        instruction.setAmount("100");
        instruction.setNetworkId(1337);
        return instruction;
    }

    private TokenConfiguration createTokenConfig() {
        TokenConfiguration config = new TokenConfiguration();
        config.setNetworkId(1337);
        config.setTokenIdentifier("0xtoken");
        config.setEnabled(true);
        config.setAllowSelfTransfer(false);
        return config;
    }
}
