package ai.generalcontracts.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProfileConfigurationTest {
    @Test
    void localProfileUsesSafeLocalDefaults() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GeneralContractApplication.class)
            .bannerMode(Banner.Mode.OFF)
            .properties(
                "spring.main.web-application-type=none",
                "spring.profiles.active=local",
                "APP_MESSAGING_ENABLED=false",
                "app.persistence.initialize=false"
            )
            .run()) {
            assertThat(context.getEnvironment().getProperty("spring.data.mongodb.uri"))
                .contains("localhost:27017")
                .contains("general-contracts");
            assertThat(context.getEnvironment().getProperty("server.address"))
                .isEqualTo("127.0.0.1");
        }
    }

    @Test
    void environmentOverridesProfileDefaults() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GeneralContractApplication.class)
            .bannerMode(Banner.Mode.OFF)
            .properties(
                "spring.main.web-application-type=none",
                "spring.profiles.active=test",
                "MONGODB_URI=mongodb://override-host:27017/override-db",
                "MONGODB_DATABASE=override-db"
            )
            .run()) {
            assertThat(context.getEnvironment().getProperty("spring.data.mongodb.uri"))
                .isEqualTo("mongodb://override-host:27017/override-db");
            assertThat(context.getEnvironment().getProperty("spring.data.mongodb.database"))
                .isEqualTo("override-db");
        }
    }

    @Test
    void productionProfileRequiresKeySettings() {
        Throwable thrown = catchThrowable(() -> new SpringApplicationBuilder(GeneralContractApplication.class)
            .bannerMode(Banner.Mode.OFF)
            .properties(
                "spring.main.web-application-type=none",
                "spring.profiles.active=prod",
                "MONGODB_URI=",
                "CHAIN_RPC_URL=http://localhost:8545",
                "SIGNER_KEY_REF="
            )
            .run());

        assertThat(thrown)
            .isInstanceOf(BeanCreationException.class);

        Throwable rootCause = thrown;
        while (rootCause.getCause() != null && !(rootCause instanceof IllegalStateException)) {
            rootCause = rootCause.getCause();
        }

        assertThat(rootCause)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing required production configuration property")
            .hasMessageContaining("MONGODB_URI");
    }
}
