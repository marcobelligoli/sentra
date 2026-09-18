package io.github.marcobelligoli.sentra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "sentra.accounts[0].username=mario",
        "sentra.accounts[0].instagram-password=secret",
        "sentra.accounts[0].api-password=mario-api-password",
        "sentra.sync.cron=-",
        "sentra.retention.cron=-",
        "sentra.session-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ActuatorIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndHasNoDetails() throws Exception {
        mvc.perform(get("/sentra/actuator/health").contextPath("/sentra"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String endpoint : new String[]{"env", "beans", "configprops", "info", "metrics", "loggers"}) {
            mvc.perform(get("/sentra/actuator/" + endpoint).contextPath("/sentra")
                            .with(httpBasic("mario", "mario-api-password")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void apiStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/sentra/api/me/fans").contextPath("/sentra")).andExpect(status().isUnauthorized());
    }

}
