package com.lifeline.battery.device;

import com.lifeline.battery.config.LifelineProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DeviceService {

    /** Published whenever the dashboard has something new to show. The alert is null if it did not change. */
    public record DeviceChanged(Device device, Alert alert) {
    }

    public record Registration(UUID deviceId, String token) {
    }

    private final DeviceRepository devices;
    private final BatteryReadingRepository readings;
    private final AlertRepository alerts;
    private final LifelineProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DeviceService(DeviceRepository devices, BatteryReadingRepository readings, AlertRepository alerts,
                         LifelineProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.devices = devices;
        this.readings = readings;
        this.alerts = alerts;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Registration register(String name, String platform) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        Device device = devices.save(
                new Device(UUID.randomUUID(), name.strip(), platform.strip(), sha256(token), clock.instant()));
        events.publishEvent(new DeviceChanged(device, null));
        return new Registration(device.getId(), token);
    }

    /** @return true while a low-battery alert is open for this device */
    @Transactional
    public boolean record(UUID deviceId, String token, int level, boolean charging) {
        Device device = devices.findForUpdate(deviceId)
                .filter(d -> sameHash(d.getTokenHash(), sha256(token)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        Instant now = clock.instant();
        if (device.applyReading(level, charging, now)) {
            readings.save(new BatteryReading(device, level, charging, now));
        }

        Alert open = alerts.findFirstByDeviceAndResolvedAtIsNull(device).orElse(null);
        Alert changed = open == null
                ? raiseIfLow(device, level, charging, now)
                : resolveIfRecovered(open, level, charging, now);
        events.publishEvent(new DeviceChanged(device, changed));

        Alert current = changed == null ? open : changed;
        return current != null && current.getResolvedAt() == null;
    }

    private Alert raiseIfLow(Device device, int level, boolean charging, Instant now) {
        if (charging || level >= properties.alert().lowBelow()) {
            return null;
        }
        return alerts.save(new Alert(device, level, now));
    }

    private Alert resolveIfRecovered(Alert alert, int level, boolean charging, Instant now) {
        if (charging) {
            alert.resolve(Alert.Resolution.CHARGING, now);
            return alert;
        }
        if (level > properties.alert().recoverAbove()) {
            alert.resolve(Alert.Resolution.RECOVERED, now);
            return alert;
        }
        return null;
    }

    @Scheduled(fixedDelayString = "${lifeline.offline-check-interval}")
    @Transactional
    public void markSilentDevicesOffline() {
        Instant cutoff = clock.instant().minus(properties.offlineAfter());
        for (Device device : devices.findSilentForUpdate(cutoff)) {
            device.markOffline();
            events.publishEvent(new DeviceChanged(device, null));
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean sameHash(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
