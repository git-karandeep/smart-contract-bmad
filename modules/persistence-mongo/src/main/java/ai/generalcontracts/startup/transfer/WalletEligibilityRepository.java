package ai.generalcontracts.startup.transfer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WalletEligibilityRepository extends MongoRepository<WalletEligibility, String> {
    Optional<WalletEligibility> findByTenantIdAndWalletAddress(String tenantId, String walletAddress);
}
