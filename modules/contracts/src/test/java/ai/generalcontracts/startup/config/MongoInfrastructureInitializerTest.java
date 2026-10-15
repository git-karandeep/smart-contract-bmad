package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoTimeoutException;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

class MongoInfrastructureInitializerTest {
    @Test
    void surfacesDatabaseFailureWithoutIncludingConnectionDetails() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        when(mongoTemplate.collectionExists("processed_events"))
            .thenThrow(new MongoTimeoutException("mongodb://user:secret@example.invalid"));

        assertThatThrownBy(() -> new MongoInfrastructureInitializer(mongoTemplate).run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to initialize required MongoDB collections and indexes")
            .hasMessageNotContaining("example.invalid");
    }

    @Test
    void createsFoundationCollectionsAndRecordsMigration() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);
        when(mongoTemplate.exists(any(org.springframework.data.mongodb.core.query.Query.class), eq("schema_migrations")))
            .thenReturn(false);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);

        new MongoInfrastructureInitializer(mongoTemplate).run(null);

        for (String collection : java.util.List.of(
            "transaction", "blockchain_transaction", "wallet", "asset", "token", "audit_event"
        )) {
            verify(mongoTemplate).collectionExists(collection);
        }
        verify(mongoTemplate).collectionExists("schema_migrations");
        verify(mongoTemplate).insert(any(Document.class), eq("schema_migrations"));

        org.mockito.ArgumentCaptor<IndexDefinition> indexes =
            org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, org.mockito.Mockito.times(10)).createIndex(indexes.capture());
        assertIndex(indexes.getAllValues(), "eventId", true);
        assertNamedIndex(indexes.getAllValues(), "provider_message_unique", "messageId", true);
        assertNamedIndex(indexes.getAllValues(), "screening_request_result_unique", "requestId", true);
        assertNamedIndex(
            indexes.getAllValues(),
            "screening_instruction_type_subject_unique",
            "subjectReference",
            true
        );
        assertIndex(indexes.getAllValues(), "publishedAt", false);
        assertIndex(indexes.getAllValues(), "idempotencyKey", true);
        assertIndex(indexes.getAllValues(), "occurredAt", false);
        assertIndex(indexes.getAllValues(), "transactionHash", true);
    }

    @Test
    void dropsLegacySingleRequestPerTypeIndexBeforeCreatingSubjectScopedUniqueness() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        IndexInfo legacyIndex = mock(IndexInfo.class);
        when(legacyIndex.getName()).thenReturn("screening_instruction_type_unique");
        when(indexOperations.getIndexInfo()).thenReturn(java.util.List.of(legacyIndex));
        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        when(mongoTemplate.exists(any(org.springframework.data.mongodb.core.query.Query.class), eq("schema_migrations")))
            .thenReturn(true);

        new MongoInfrastructureInitializer(mongoTemplate).run(null);

        verify(indexOperations).dropIndex("screening_instruction_type_unique");
        org.mockito.ArgumentCaptor<IndexDefinition> indexes =
            org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, org.mockito.Mockito.times(7)).createIndex(indexes.capture());
        assertNamedIndex(
            indexes.getAllValues(),
            "screening_instruction_type_subject_unique",
            "subjectReference",
            true
        );
    }

    @Test
    void createsMissingFoundationCollections() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.collectionExists(anyString())).thenReturn(false);
        when(mongoTemplate.exists(any(org.springframework.data.mongodb.core.query.Query.class), eq("schema_migrations")))
            .thenReturn(false);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);

        new MongoInfrastructureInitializer(mongoTemplate).run(null);

        for (String collection : java.util.List.of(
            "schema_migrations",
            "transfer_instructions",
            "transfer_audit",
            "screening_requests",
            "screening_results",
            "transfer_submissions",
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
            verify(mongoTemplate).createCollection(collection);
        }
    }

    @Test
    void acceptsCollectionsCreatedByAnotherApplicationInstance() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);
        when(mongoTemplate.collectionExists("schema_migrations")).thenReturn(false, true);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        when(mongoTemplate.exists(any(org.springframework.data.mongodb.core.query.Query.class), eq("schema_migrations")))
            .thenReturn(true);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("collection already exists"))
            .when(mongoTemplate).createCollection("schema_migrations");

        new MongoInfrastructureInitializer(mongoTemplate).run(null);

        verify(mongoTemplate).createCollection("schema_migrations");
    }

    @Test
    void acceptsMigrationMarkerWrittenByAnotherApplicationInstance() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.collectionExists(anyString())).thenReturn(true);
        when(mongoTemplate.exists(any(org.springframework.data.mongodb.core.query.Query.class), eq("schema_migrations")))
            .thenReturn(false, true);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOperations);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("migration marker already exists"))
            .when(mongoTemplate).insert(any(Document.class), eq("schema_migrations"));

        new MongoInfrastructureInitializer(mongoTemplate).run(null);

        verify(mongoTemplate).insert(any(Document.class), eq("schema_migrations"));
    }

    private void assertIndex(java.util.List<IndexDefinition> indexes, String field, boolean unique) {
        assertThat(indexes)
            .filteredOn(index -> index.getIndexKeys().containsKey(field))
            .singleElement()
            .satisfies(index -> assertThat(Boolean.TRUE.equals(index.getIndexOptions().get("unique"))).isEqualTo(unique));
    }

    private void assertNamedIndex(
        java.util.List<IndexDefinition> indexes,
        String name,
        String field,
        boolean unique
    ) {
        assertThat(indexes)
            .filteredOn(index -> index.getIndexKeys().containsKey(field))
            .filteredOn(index -> name.equals(index.getIndexOptions().get("name")))
            .singleElement()
            .satisfies(index -> assertThat(Boolean.TRUE.equals(index.getIndexOptions().get("unique"))).isEqualTo(unique));
    }
}
