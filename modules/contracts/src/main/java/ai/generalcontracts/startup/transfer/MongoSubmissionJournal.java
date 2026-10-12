package ai.generalcontracts.startup.transfer;

import ai.generalcontracts.chain.SubmissionIntent;
import ai.generalcontracts.chain.SubmissionJournal;
import ai.generalcontracts.chain.TransferSubmission;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoSubmissionJournal implements SubmissionJournal {
    private final MongoTemplate mongoTemplate;

    public MongoSubmissionJournal(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean claim(SubmissionIntent intent) {
        try {
            mongoTemplate.insert(new TransferSubmissionRecord(intent));
            return true;
        } catch (DuplicateKeyException alreadyClaimed) {
            return false;
        }
    }

    @Override
    public Optional<TransferSubmission> find(String instructionId) {
        if (instructionId == null || instructionId.isBlank()) {
            throw new IllegalArgumentException("Transfer instruction ID is required");
        }
        return Optional.ofNullable(mongoTemplate.findById(instructionId, TransferSubmissionRecord.class))
            .map(TransferSubmissionRecord::toSubmission);
    }

    @Override
    public void record(TransferSubmission submission) {
        Update update = new Update()
            .set("status", submission.outcome().name())
            .set("reasonCode", submission.reasonCode());
        if (submission.transactionHash() == null) {
            update.unset("transactionHash");
        } else {
            update.set("transactionHash", submission.transactionHash());
        }
        var result = mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(submission.instructionId())),
            update,
            TransferSubmissionRecord.class
        );
        if (result.getMatchedCount() != 1) {
            throw new IllegalStateException("Transfer submission claim is missing");
        }
    }
}
