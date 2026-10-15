package ai.generalcontracts.startup.transfer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NetworkConfigurationRepository extends MongoRepository<NetworkConfiguration, String> {
    Optional<NetworkConfiguration> findByNetworkId(long networkId);
}
