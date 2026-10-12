package ai.generalcontracts.startup.transfer;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransferInstructionRepository extends MongoRepository<TransferInstruction, String> {
    Optional<TransferInstruction> findByTenantIdAndInitiatingPrincipalAndIdempotencyKey(
        String tenantId,
        String initiatingPrincipal,
        String idempotencyKey
    );
}
