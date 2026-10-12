package ai.generalcontracts.startup.transfer;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransferAuditRepository extends MongoRepository<TransferAuditRecord, String> {
}
