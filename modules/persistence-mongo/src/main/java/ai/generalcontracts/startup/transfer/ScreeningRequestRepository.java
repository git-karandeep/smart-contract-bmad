package ai.generalcontracts.startup.transfer;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ScreeningRequestRepository extends MongoRepository<ScreeningRequestRecord, String> {
    Optional<ScreeningRequestRecord> findByRequestId(String requestId);

    Optional<ScreeningRequestRecord> findByInstructionIdAndScreeningType(String instructionId, String screeningType);

    java.util.List<ScreeningRequestRecord> findAllByInstructionId(String instructionId);
}
