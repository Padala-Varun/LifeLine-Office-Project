package com.lifeline.battery;

import com.lifeline.battery.config.LifelineProperties;
import com.lifeline.battery.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The settings used on the public server: a join code, and limits on guessing. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public",
        "LIFELINE_DASHBOARD_PASSWORD=cloud-password",
        "LIFELINE_JOIN_CODE=ABCD2345",
        "lifeline.offline-check-interval=1h"
})
@ActiveProfiles("cloud")
@AutoConfigureMockMvc
class PublicServerTests {

    @Autowired MockMvc mvc;
    @Autowired DeviceRepository devices;

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private ResultActions join(String codeJson, String address) throws Exception {
        return mvc.perform(post("/api/device/register").with(from(address)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Phone\",\"platform\":\"Android\"%s}".formatted(codeJson)));
    }

    private ResultActions login(String password, String address) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).with(from(address))
                .param("username", "admin").param("password", password));
    }

    @Test
    void devicesNeedTheJoinCode() throws Exception {
        long before = devices.count();
        join("", "10.0.0.1").andExpect(status().isForbidden());
        join(",\"joinCode\":\"\"", "10.0.0.1").andExpect(status().isForbidden());
        join(",\"joinCode\":\"WRONG\"", "10.0.0.1").andExpect(status().isForbidden());
        assertThat(devices.count()).isEqualTo(before);

        join(",\"joinCode\":\"ABCD2345\"", "10.0.0.1").andExpect(status().isCreated());
        join(",\"joinCode\":\" abcd2345 \"", "10.0.0.1").andExpect(status().isCreated());
        assertThat(devices.count()).isEqualTo(before + 2);
    }

    @Test
    void guessingTheJoinCodeIsStopped() throws Exception {
        for (int i = 0; i < 10; i++) {
            join(",\"joinCode\":\"GUESS" + i + "\"", "10.0.0.2").andExpect(status().isForbidden());
        }
        join(",\"joinCode\":\"ABCD2345\"", "10.0.0.2").andExpect(status().isTooManyRequests());
        join(",\"joinCode\":\"ABCD2345\"", "10.0.0.3").andExpect(status().isCreated());
    }

    @Test
    void guessingThePasswordIsStopped() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("guess" + i, "10.0.0.4").andExpect(status().isUnauthorized());
        }
        login("cloud-password", "10.0.0.4").andExpect(status().isTooManyRequests());
        login("cloud-password", "10.0.0.5").andExpect(status().isNoContent());
    }

    @Test
    void aCorrectLoginClearsEarlierMistakes() throws Exception {
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 9; i++) {
                login("typo", "10.0.0.6").andExpect(status().isUnauthorized());
            }
            login("cloud-password", "10.0.0.6").andExpect(status().isNoContent());
        }
    }

    @Test
    void refusesToStartWithoutItsSecrets() {
        for (String missing : new String[]{null, "", " ", "${LIFELINE_DASHBOARD_PASSWORD}"}) {
            assertThatThrownBy(() -> publicServer(missing, "ABCD2345")).hasMessageContaining("LIFELINE_DASHBOARD_PASSWORD");
            assertThatThrownBy(() -> publicServer("cloud-password", missing)).hasMessageContaining("LIFELINE_JOIN_CODE");
        }
    }

    private static LifelineProperties publicServer(String password, String joinCode) {
        return new LifelineProperties(new LifelineProperties.Alert(25, 30), Duration.ofSeconds(150),
                Duration.ofSeconds(15), new LifelineProperties.Dashboard("admin", password), joinCode, true, null);
    }

    @Test
    void theLocalPasswordDoesNotWorkHere() throws Exception {
        login("varun", "10.0.0.7").andExpect(status().isUnauthorized());
    }
}
