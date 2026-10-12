package ai.generalcontracts.startup.config;

import java.util.List;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.persistence.initialize", havingValue = "true", matchIfMissing = true)
public class MongoInfrastructureInitializer implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;

    public MongoInfrastructureInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            createCollectionIfMissing("schema_migrations");
            createCollectionIfMissing("transfer_instructions");
            createCollectionIfMissing("transfer_audit");
            createCollectionIfMissing("screening_requests");
            createCollectionIfMissing("screening_results");
            createCollectionIfMissing("transfer_submissions");
            mongoTemplate.indexOps("transfer_instructions").createIndex(
                new Index()
                    .on("tenantId", Sort.Direction.ASC)
                    .on("initiatingPrincipal", Sort.Direction.ASC)
                    .on("idempotencyKey", Sort.Direction.ASC)
                    .unique()
                    .named("transfer_principal_idempotency_unique")
            );
            mongoTemplate.indexOps("transfer_audit").createIndex(
                new Index().on("instructionId", Sort.Direction.ASC)
                    .on("occurredAt", Sort.Direction.ASC).named("transfer_audit_timeline")
            );
            mongoTemplate.indexOps("screening_requests").createIndex(
                new Index().on("instructionId", Sort.Direction.ASC)
                    .on("screeningType", Sort.Direction.ASC).unique().named("screening_instruction_type_unique")
            );
            mongoTemplate.indexOps("screening_requests").createIndex(
                new Index().on("correlationId", Sort.Direction.ASC).unique().named("correlation_id_unique")
            );
            mongoTemplate.indexOps("screening_results").createIndex(
                new Index().on("providerId", Sort.Direction.ASC)
                    .on("messageId", Sort.Direction.ASC).unique().named("provider_message_unique")
            );
            mongoTemplate.indexOps("screening_results").createIndex(
                new Index().on("requestId", Sort.Direction.ASC)
                    .unique().named("screening_request_result_unique")
            );
            mongoTemplate.indexOps("transfer_submissions").createIndex(
                new Index().on("transactionHash", Sort.Direction.ASC)
                    .unique().sparse().named("transaction_hash_unique_sparse")
            );
            if (migrationApplied()) {
                return;
            }
            for (String collection : List.of(
                "transaction",
                "blockchain_transaction",
                "wallet",
                "asset",
                "token",
                "audit_event",
                "processed_events",
                "outbox",
                "projection_checkpoints"
            )) {
                createCollectionIfMissing(collection);
            }
            mongoTemplate.indexOps("processed_events")
                .createIndex(new Index().on("eventId", Sort.Direction.ASC).unique());
            mongoTemplate.indexOps("outbox")
                .createIndex(new Index().on("messageId", Sort.Direction.ASC).unique());
            mongoTemplate.indexOps("outbox")
                .createIndex(new Index().on("publishedAt", Sort.Direction.ASC));
            try {
                mongoTemplate.insert(
                    new Document("_id", "V1")
                        .append("description", "Create foundation collections")
                    .append("appliedAt", new java.util.Date()),
                    "schema_migrations"
                );
            } catch (DuplicateKeyException exception) {
                if (!migrationApplied()) {
                    throw exception;
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                "Unable to initialize required MongoDB collections and indexes; check database availability and configuration ("
                    + exception.getClass().getSimpleName() + ")"
            );
        }
    }

    private void createCollectionIfMissing(String collection) {
        if (mongoTemplate.collectionExists(collection)) {
            return;
        }
        try {
            mongoTemplate.createCollection(collection);
        } catch (DataAccessException exception) {
            if (!mongoTemplate.collectionExists(collection)) {
                throw exception;
            }
        }
    }

    private boolean migrationApplied() {
        return mongoTemplate.exists(Query.query(Criteria.where("_id").is("V1")), "schema_migrations");
    }
}
