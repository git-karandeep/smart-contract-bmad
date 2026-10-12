package ai.generalcontracts.startup.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.generalcontracts.chain.SubmissionIntent;
import ai.generalcontracts.chain.TransferSubmission;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoSubmissionJournalTest {
    @Test
    void claimsEachInstructionAtMostOnceUsingMongoUniqueKey() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoSubmissionJournal journal = new MongoSubmissionJournal(mongo);

        assertTrue(journal.claim(intent()));
        doThrow(new DuplicateKeyException("duplicate")).when(mongo).insert(any(TransferSubmissionRecord.class));
        assertFalse(journal.claim(intent()));
    }

    @Test
    void persistsSubmissionStateAndUnsetsMissingHashes() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getMatchedCount()).thenReturn(1L);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(TransferSubmissionRecord.class)))
            .thenReturn(updateResult);
        MongoSubmissionJournal journal = new MongoSubmissionJournal(mongo);
        TransferSubmission submission = new TransferSubmission(
            "transfer-1", 1337, "0x3333333333333333333333333333333333333333",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222", "10.25", null,
            TransferSubmission.Outcome.UNKNOWN, "CHAIN_SUBMISSION_OUTCOME_UNKNOWN"
        );

        journal.record(submission);

        org.mockito.ArgumentCaptor<Update> update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(TransferSubmissionRecord.class));
        Document operations = update.getValue().getUpdateObject();
        assertEquals("UNKNOWN", ((Document) operations.get("$set")).get("status"));
        assertTrue(((Document) operations.get("$unset")).containsKey("transactionHash"));
    }

    @Test
    void recoversPreviouslyRecordedSubmissionHash() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        TransferSubmissionRecord record = new TransferSubmissionRecord(intent());
        record.apply(new TransferSubmission(
            "transfer-1", 1337, intent().tokenContract(), intent().source(), intent().destination(),
            intent().decimalAmount(), "0xhash", TransferSubmission.Outcome.SUBMITTED, null
        ));
        when(mongo.findById("transfer-1", TransferSubmissionRecord.class)).thenReturn(record);
        MongoSubmissionJournal journal = new MongoSubmissionJournal(mongo);

        TransferSubmission recovered = journal.find("transfer-1").orElseThrow();

        assertEquals(TransferSubmission.Outcome.SUBMITTED, recovered.outcome());
        assertEquals("0xhash", recovered.transactionHash());
    }

    private SubmissionIntent intent() {
        return new SubmissionIntent(
            "transfer-1", 1337, "0x3333333333333333333333333333333333333333",
            "0x1111111111111111111111111111111111111111",
            "0x2222222222222222222222222222222222222222", "10.25"
        );
    }
}
