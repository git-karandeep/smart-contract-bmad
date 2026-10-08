package ai.generalcontracts.startup.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.generalcontracts.startup.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class OrderApiTest {
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(Mockito.mock(OrderService.class)))
            .setValidator(validator)
            .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    @Tag("api")
    void rejectsInvalidOrderInput() throws Exception {
        mockMvc.perform(post("/orders")
                .contentType("application/json")
                .content("{\"customer\":\"\",\"amount\":-1,\"status\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
