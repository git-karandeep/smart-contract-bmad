package ai.generalcontracts.startup.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class GrpcClientConfigurationTest {
    @Test
    void buildsChannelFromConfiguredHttpUrl() throws Exception {
        ConfigProperties properties = new ConfigProperties();
        GrpcClientConfiguration configuration =
            new GrpcClientConfiguration(properties, new DefaultResourceLoader());

        ManagedChannel channel = configuration.grpcChannel("http://localhost:26500");
        try {
            assertThat(channel.authority()).isEqualTo("localhost:26500");
        } finally {
            channel.shutdownNow();
        }
    }
}
