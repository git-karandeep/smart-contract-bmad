package ai.generalcontracts.startup.transfer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TokenConfigurationRepository extends MongoRepository<TokenConfiguration, String> {
    Optional<TokenConfiguration> findByNetworkIdAndTokenIdentifier(long networkId, String tokenIdentifier);
}
