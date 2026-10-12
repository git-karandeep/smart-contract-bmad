package ai.generalcontracts.startup.transfer;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScreeningResultRepository extends MongoRepository<ScreeningResultRecord, String> {
    Optional<ScreeningResultRecord> findByProviderIdAndMessageId(String providerId, String messageId);

    Optional<ScreeningResultRecord> findByInstructionIdAndScreeningType(String instructionId, String screeningType);
}
