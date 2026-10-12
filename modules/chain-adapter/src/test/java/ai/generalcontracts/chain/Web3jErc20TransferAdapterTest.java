package ai.generalcontracts.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.tx.TransactionManager;

class Web3jErc20TransferAdapterTest {
    private static final String TOKEN = "0x3333333333333333333333333333333333333333";
    private static final String SOURCE = "0x1111111111111111111111111111111111111111";
    private static final String DESTINATION = "0x2222222222222222222222222222222222222222";
    private static final String HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void reusesRecordedSubmissionOnRetryWithoutContactingRpc() throws java.io.IOException {
        Web3j web3j = mock(Web3j.class);
        TransactionManager manager = mock(TransactionManager.class);
        SubmissionJournal journal = mock(SubmissionJournal.class);
        SubmissionIntent intent = new SubmissionIntent("transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "10");
        TransferSubmission recorded = new TransferSubmission(
            "transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "10", HASH,
            TransferSubmission.Outcome.SUBMITTED, null
        );
        when(journal.claim(intent)).thenReturn(false);
        when(journal.find("transfer-1")).thenReturn(Optional.of(recorded));
        Web3jErc20TransferAdapter adapter = adapter(web3j, manager, journal);

        TransferSubmission result = adapter.submit(
            new Erc20TransferCommand("transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "10")
        );

        assertEquals(TransferSubmission.Outcome.SUBMITTED, result.outcome());
        assertEquals(HASH, result.transactionHash());
        verify(web3j, never()).ethChainId();
        verify(manager, never()).sendTransaction(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void conflictingJournaledIntentIsNotSubmittedAgain() {
        Web3j web3j = mock(Web3j.class);
        TransactionManager manager = mock(TransactionManager.class);
        SubmissionJournal journal = mock(SubmissionJournal.class);
        when(journal.claim(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(journal.find("transfer-1")).thenReturn(Optional.of(new TransferSubmission(
            "transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "9", HASH,
            TransferSubmission.Outcome.SUBMITTED, null
        )));
        Web3jErc20TransferAdapter adapter = adapter(web3j, manager, journal);

        TransferSubmission result = adapter.submit(
            new Erc20TransferCommand("transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "10")
        );

        assertEquals(TransferSubmission.Outcome.UNKNOWN, result.outcome());
        assertEquals("SUBMISSION_CLAIM_CONFLICT_OR_RECORD_UNAVAILABLE", result.reasonCode());
        verify(web3j, never()).ethChainId();
    }

    private Web3jErc20TransferAdapter adapter(
        Web3j web3j,
        TransactionManager manager,
        SubmissionJournal journal
    ) {
        return new Web3jErc20TransferAdapter(
            web3j, manager, BigInteger.valueOf(1337), BigInteger.ZERO, BigInteger.valueOf(100_000),
            SOURCE, Map.of(TOKEN, 18), journal
        );
    }
}
