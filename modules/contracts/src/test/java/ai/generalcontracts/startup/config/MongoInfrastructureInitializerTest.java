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
import org.springframework.data.mongodb.core.index.IndexOperations;

class MongoInfrastructureInitializerTest {
    @Test
    void surfacesDatabaseFailureWithoutIncludingConnectionDetails() {
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
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
        verify(indexOperations, org.mockito.Mockito.times(3)).createIndex(indexes.capture());
        assertIndex(indexes.getAllValues(), "eventId", true);
        assertIndex(indexes.getAllValues(), "messageId", true);
        assertIndex(indexes.getAllValues(), "publishedAt", false);
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
        when(mongoTemplate.collectionExists("schema_migrations")).thenReturn(false, true);
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
}
