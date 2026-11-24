package com.lifeline.battery;

import com.jayway.jsonpath.JsonPath;
import com.lifeline.battery.device.AlertRepository;
import com.lifeline.battery.device.BatteryReadingRepository;
import com.lifeline.battery.device.DeviceRepository;
import com.lifeline.battery.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test",
        "lifeline.dashboard.password=test-password",
        "lifeline.offline-check-interval=1h"
})
@AutoConfigureMockMvc
class BatteryMonitorTests {

    /** A clock the tests can move forward. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T09:00:00Z");

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }

    @Autowired MockMvc mvc;
    @Autowired TestClock clock;
    @Autowired DeviceService service;
    @Autowired DeviceRepository devices;
    @Autowired AlertRepository alerts;
    @Autowired BatteryReadingRepository readings;

    String deviceId;
    String token;

    @BeforeEach
    void registerDevice() throws Exception {
        alerts.deleteAll();
        readings.deleteAll();
        devices.deleteAll();
        String body = mvc.perform(post("/api/device/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Front desk tablet\",\"platform\":\"Android\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        deviceId = JsonPath.read(body, "$.deviceId");
        token = JsonPath.read(body, "$.token");
    }

    ResultActions send(String bearer, String level, boolean charging) throws Exception {
        return mvc.perform(post("/api/device/readings").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + bearer)
                .content("{\"deviceId\":\"%s\",\"level\":%s,\"charging\":%s}".formatted(deviceId, level, charging)));
    }

    void expectAlertOpen(int level, boolean charging, boolean open) throws Exception {
        send(token, String.valueOf(level), charging)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertOpen").value(open));
    }

    @Test
    void alertsOnlyBelow25() throws Exception {
        expectAlertOpen(26, false, false);
        expectAlertOpen(25, false, false);
        expectAlertOpen(24, false, true);
        assertThat(alerts.count()).isEqualTo(1);
    }

    @Test
    void aFirstReadingThatIsAlreadyLowAlerts() throws Exception {
        expectAlertOpen(7, false, true);
    }

    @Test
    void oneAlertPerDropUntilAbove30() throws Exception {
        expectAlertOpen(24, false, true);
        expectAlertOpen(23, false, true);
        expectAlertOpen(22, false, true);
        expectAlertOpen(26, false, true);
        expectAlertOpen(30, false, true);
        assertThat(alerts.count()).isEqualTo(1);

        expectAlertOpen(31, false, false);
        expectAlertOpen(24, false, true);
        assertThat(alerts.count()).isEqualTo(2);
    }

    @Test
    void chargingClearsTheAlertAndUnpluggingWhileLowRaisesANewOne() throws Exception {
        expectAlertOpen(20, false, true);
        expectAlertOpen(20, true, false);
        expectAlertOpen(21, true, false);
        expectAlertOpen(21, false, true);
        assertThat(alerts.count()).isEqualTo(2);
    }

    @Test
    void heartbeatsWithTheSameValueAreNotStoredAgain() throws Exception {
        expectAlertOpen(80, false, false);
        expectAlertOpen(80, false, false);
        expectAlertOpen(79, false, false);
        assertThat(readings.count()).isEqualTo(2);
    }

    @Test
    void rejectsWrongTokenAndBadValues() throws Exception {
        send("wrong", "10", false).andExpect(status().isUnauthorized());
        send(token, "101", false).andExpect(status().isBadRequest());
        send(token, "-1", false).andExpect(status().isBadRequest());
        send(token, "null", false).andExpect(status().isBadRequest());
        assertThat(alerts.count()).isZero();
    }

    @Test
    void oneDeviceCannotReportForAnother() throws Exception {
        String firstToken = token;
        registerDeviceAgain();
        send(firstToken, "10", false).andExpect(status().isUnauthorized());
    }

    private void registerDeviceAgain() throws Exception {
        String body = mvc.perform(post("/api/device/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Other\",\"platform\":\"Windows\"}")).andReturn().getResponse().getContentAsString();
        deviceId = JsonPath.read(body, "$.deviceId");
    }

    @Test
    void silentDevicesGoOfflineAndComeBack() throws Exception {
        expectAlertOpen(50, false, false);
        clock.now = clock.now.plus(Duration.ofSeconds(149));
        service.markSilentDevicesOffline();
        assertThat(devices.findAll().getFirst().isOnline()).isTrue();

        clock.now = clock.now.plus(Duration.ofSeconds(2));
        service.markSilentDevicesOffline();
        assertThat(devices.findAll().getFirst().isOnline()).isFalse();

        expectAlertOpen(50, false, false);
        assertThat(devices.findAll().getFirst().isOnline()).isTrue();
    }

    @Test
    void dashboardDataNeedsALogin() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    @WithMockUser
    void dashboardShowsDevicesAndAlertsButNoSecrets() throws Exception {
        expectAlertOpen(24, false, true);
        String body = mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowBelow").value(25))
                .andExpect(jsonPath("$.devices[0].level").value(24))
                .andExpect(jsonPath("$.openAlerts[0].deviceName").value("Front desk tablet"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("token");
    }

    @Test
    void loginChecksPasswordAndCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "test-password"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "nope"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "test-password"))
                .andExpect(status().is4xxClientError());
    }
}
