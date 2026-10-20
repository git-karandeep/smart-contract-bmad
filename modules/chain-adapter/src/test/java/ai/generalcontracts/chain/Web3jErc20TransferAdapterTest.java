package ai.generalcontracts.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
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

    @Test
    void exposesInstructionKeyedJournalLookupForWorkflowRecovery() {
        TransferSubmission journaled = new TransferSubmission(
            "transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "10", HASH,
            TransferSubmission.Outcome.UNKNOWN, "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
        );
        SubmissionJournal journal = mock(SubmissionJournal.class);
        when(journal.find("transfer-1")).thenReturn(Optional.of(journaled));
        Web3jErc20TransferAdapter adapter = adapter(mock(Web3j.class), mock(TransactionManager.class), journal);

        assertEquals(Optional.of(journaled), adapter.findJournaledSubmission("transfer-1"));
        verify(journal).find("transfer-1");
    }

    @Test
    void networkTokenAndSignerMismatchesNeverClaimOrSubmit() {
        TransactionManager manager = mock(TransactionManager.class);
        SubmissionJournal journal = mock(SubmissionJournal.class);
        Web3jErc20TransferAdapter adapter = adapter(mock(Web3j.class), manager, journal);

        for (Erc20TransferCommand mismatch : new Erc20TransferCommand[] {
            new Erc20TransferCommand("transfer-1", 1, TOKEN, SOURCE, DESTINATION, "10"),
            new Erc20TransferCommand("transfer-1", 1337, DESTINATION, SOURCE, DESTINATION, "10"),
            new Erc20TransferCommand("transfer-1", 1337, TOKEN, DESTINATION, SOURCE, "10")
        }) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> adapter.submit(mismatch));
        }
        verify(journal, never()).claim(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitsExactBaseUnitsAndReportsSubmittedWithoutCompletion() throws java.io.IOException {
        Web3j web3j = chainReady();
        TransactionManager manager = mock(TransactionManager.class);
        SubmissionJournal journal = mock(SubmissionJournal.class);
        when(journal.claim(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.web3j.protocol.core.methods.response.EthSendTransaction sent =
            new org.web3j.protocol.core.methods.response.EthSendTransaction();
        sent.setResult(HASH);
        when(manager.sendTransaction(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(sent);

        TransferSubmission result = adapter(web3j, manager, journal).submit(
            new Erc20TransferCommand("transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "1.5")
        );

        assertEquals(TransferSubmission.Outcome.SUBMITTED, result.outcome());
        assertEquals(HASH, result.transactionHash());
        org.mockito.ArgumentCaptor<String> call = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(manager).sendTransaction(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(TOKEN), call.capture(), org.mockito.ArgumentMatchers.any()
        );
        assertEquals(
            "%064x".formatted(new BigInteger("1500000000000000000")),
            call.getValue().substring(call.getValue().length() - 64)
        );
        verify(journal).record(result);
    }

    @Test
    void nodeRejectionAndTransportFailureStayNonFinal() throws java.io.IOException {
        Web3j web3j = chainReady();
        TransactionManager manager = mock(TransactionManager.class);
        SubmissionJournal journal = mock(SubmissionJournal.class);
        when(journal.claim(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.web3j.protocol.core.methods.response.EthSendTransaction rejected =
            new org.web3j.protocol.core.methods.response.EthSendTransaction();
        rejected.setError(new org.web3j.protocol.core.Response.Error(-32000, "reverted"));
        when(manager.sendTransaction(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(rejected).thenThrow(new java.io.IOException("timeout"));
        Web3jErc20TransferAdapter adapter = adapter(web3j, manager, journal);
        Erc20TransferCommand command = new Erc20TransferCommand("transfer-1", 1337, TOKEN, SOURCE, DESTINATION, "1");

        TransferSubmission first = adapter.submit(command);
        TransferSubmission second = adapter.submit(command);

        assertEquals(TransferSubmission.Outcome.REJECTED, first.outcome());
        assertEquals(null, first.transactionHash());
        assertEquals(TransferSubmission.Outcome.UNKNOWN, second.outcome());
        assertEquals("CHAIN_SUBMISSION_OUTCOME_UNKNOWN", second.reasonCode());
    }

    private Web3j chainReady() throws java.io.IOException {
        Web3j web3j = mock(Web3j.class);
        @SuppressWarnings("unchecked")
        org.web3j.protocol.core.Request<?, org.web3j.protocol.core.methods.response.EthChainId> request =
            mock(org.web3j.protocol.core.Request.class);
        org.web3j.protocol.core.methods.response.EthChainId chain =
            new org.web3j.protocol.core.methods.response.EthChainId();
        chain.setResult("0x539");
        doReturn(request).when(web3j).ethChainId();
        doReturn(chain).when(request).send();
        return web3j;
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
