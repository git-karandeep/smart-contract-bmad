package ai.generalcontracts.chain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;

public final class Web3jErc20TransferAdapter {
    private static final Pattern ADDRESS = Pattern.compile("(?i)^0x[0-9a-f]{40}$");
    private final Web3j web3j;
    private final TransactionManager transactionManager;
    private final BigInteger expectedChainId;
    private final BigInteger gasPrice;
    private final BigInteger gasLimit;
    private final String authorizedSignerAddress;
    private final Map<String, Integer> configuredTokenDecimals;
    private final SubmissionJournal journal;

    public Web3jErc20TransferAdapter(
        Web3j web3j,
        TransactionManager transactionManager,
        BigInteger expectedChainId,
        BigInteger gasPrice,
        BigInteger gasLimit,
        String authorizedSignerAddress,
        Map<String, Integer> configuredTokenDecimals,
        SubmissionJournal journal
    ) {
        if (expectedChainId == null || expectedChainId.signum() <= 0
            || gasPrice == null || gasPrice.signum() < 0
            || gasLimit == null || gasLimit.signum() <= 0) {
            throw new IllegalArgumentException("Chain and transaction configuration must be valid");
        }
        this.web3j = Objects.requireNonNull(web3j);
        this.transactionManager = Objects.requireNonNull(transactionManager);
        this.expectedChainId = expectedChainId;
        this.gasPrice = gasPrice;
        this.gasLimit = gasLimit;
        this.authorizedSignerAddress = normalizeAddress(authorizedSignerAddress);
        this.configuredTokenDecimals = Objects.requireNonNull(configuredTokenDecimals).entrySet().stream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                entry -> normalizeAddress(entry.getKey()), Map.Entry::getValue
            )
        );
        this.journal = Objects.requireNonNull(journal);
    }

    public TransferSubmission submit(Erc20TransferCommand command) {
        requireAddress(command.tokenContract(), "token contract");
        requireAddress(command.source(), "source");
        requireAddress(command.destination(), "destination");
        if (command.instructionId() == null || command.instructionId().isBlank()
            || !expectedChainId.equals(BigInteger.valueOf(command.networkId()))
            || !authorizedSignerAddress.equals(normalizeAddress(command.source()))) {
            throw new IllegalArgumentException("Transfer does not match configured instruction or chain");
        }
        Integer decimals = configuredTokenDecimals.get(normalizeAddress(command.tokenContract()));
        if (decimals == null) {
            throw new IllegalArgumentException("Token contract is not configured for the selected network");
        }
        BigInteger baseUnits = TokenAmountConverter.toBaseUnits(command.decimalAmount(), decimals);
        SubmissionIntent intent = new SubmissionIntent(
            command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
            normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount()
        );
        if (!journal.claim(intent)) {
            return journal.find(command.instructionId())
                .filter(existing -> matches(existing, intent))
                .orElseGet(() -> new TransferSubmission(
                    command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                    normalizeAddress(command.source()), normalizeAddress(command.destination()),
                    command.decimalAmount(), null, TransferSubmission.Outcome.UNKNOWN,
                    "SUBMISSION_CLAIM_CONFLICT_OR_RECORD_UNAVAILABLE"
                ));
        }
        try {
            var chainIdResponse = web3j.ethChainId().send();
            if (chainIdResponse.hasError() || !expectedChainId.equals(chainIdResponse.getChainId())) {
                throw new IllegalStateException("Configured RPC endpoint is not on the expected chain");
            }
        } catch (IOException exception) {
            return persist(new TransferSubmission(
                command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount(), null,
                TransferSubmission.Outcome.UNKNOWN, "CHAIN_RPC_UNAVAILABLE"
            ));
        }

        Function transfer = new Function(
            "transfer",
            List.of(new Address(normalizeAddress(command.destination())), new Uint256(baseUnits)),
            List.of(new TypeReference<Bool>() { })
        );
        String encodedCall = FunctionEncoder.encode(transfer);
        try {
            EthSendTransaction response = transactionManager.sendTransaction(
                gasPrice, gasLimit, normalizeAddress(command.tokenContract()), encodedCall, BigInteger.ZERO
            );
            if (response.hasError()) {
                return persist(new TransferSubmission(
                    command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                    normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount(), null,
                    TransferSubmission.Outcome.REJECTED, "CHAIN_SUBMISSION_REJECTED"
                ));
            }
            String hash = response.getTransactionHash();
            if (hash == null || hash.isBlank()) {
                return persist(new TransferSubmission(
                    command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                    normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount(), null,
                    TransferSubmission.Outcome.UNKNOWN, "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
                ));
            }
            return persist(new TransferSubmission(
                command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount(), hash,
                TransferSubmission.Outcome.SUBMITTED, null
            ));
        } catch (IOException exception) {
            return persist(new TransferSubmission(
                command.instructionId(), command.networkId(), normalizeAddress(command.tokenContract()),
                normalizeAddress(command.source()), normalizeAddress(command.destination()), command.decimalAmount(), null,
                TransferSubmission.Outcome.UNKNOWN, "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
            ));
        }
    }

    private TransferSubmission persist(TransferSubmission submission) {
        try {
            journal.record(submission);
            return submission;
        } catch (RuntimeException exception) {
            return new TransferSubmission(
                submission.instructionId(), submission.networkId(), submission.tokenContract(),
                submission.source(), submission.destination(), submission.amount(), submission.transactionHash(),
                TransferSubmission.Outcome.UNKNOWN, "SUBMISSION_JOURNAL_UNAVAILABLE"
            );
        }
    }

    private boolean matches(TransferSubmission existing, SubmissionIntent intent) {
        try {
            return intent.instructionId().equals(existing.instructionId())
                && intent.networkId() == existing.networkId()
                && intent.tokenContract().equalsIgnoreCase(existing.tokenContract())
                && intent.source().equalsIgnoreCase(existing.source())
                && intent.destination().equalsIgnoreCase(existing.destination())
                && new java.math.BigDecimal(intent.decimalAmount())
                    .compareTo(new java.math.BigDecimal(existing.amount())) == 0;
        } catch (NumberFormatException invalidAmount) {
            return false;
        }
    }

    private void requireAddress(String value, String label) {
        if (value == null || !ADDRESS.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + " address");
        }
    }

    private String normalizeAddress(String value) {
        requireAddress(value, "EVM");
        return value.toLowerCase(Locale.ROOT);
    }
}
