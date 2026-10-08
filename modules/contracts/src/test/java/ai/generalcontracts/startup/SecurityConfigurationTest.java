package ai.generalcontracts.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    properties = {
        "management.health.mongo.enabled=false",
        "spring.mongodb.uri=mongodb://127.0.0.1:1/health-test?directConnection=true",
        "spring.mongodb.ssl.enabled=false"
    }
)
@ActiveProfiles("test")
class SecurityConfigurationTest {
    @Autowired
    private WebApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiresCsrfTokenForAuthenticatedStateChanges() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(post("/orders")
                    .with(httpBasic("test-user", "test-only"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customer\":\"\",\"amount\":0,\"status\":\"\"}"))
                .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/api/security/csrf")
                    .with(httpBasic("test-user", "test-only")))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(post("/orders")
                    .with(httpBasic("test-user", "test-only"))
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customer\":\"\",\"amount\":0,\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
