package com.lifeline.battery.dashboard;

import com.lifeline.battery.device.Alert;
import com.lifeline.battery.device.Device;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the dashboard is allowed to see. Token hashes never leave the server. */
public final class DashboardViews {

    private DashboardViews() {
    }

    public record DeviceView(UUID id, String name, String platform, Integer level, Boolean charging,
                             boolean online, Instant lastSeenAt) {
        static DeviceView of(Device d) {
            return new DeviceView(d.getId(), d.getName(), d.getPlatform(), d.getLevel(), d.getCharging(),
                    d.isOnline(), d.getLastSeenAt());
        }
    }

    public record AlertView(Long id, UUID deviceId, String deviceName, int level, Instant raisedAt,
                            Instant resolvedAt, Alert.Resolution resolution) {
        static AlertView of(Alert a) {
            return new AlertView(a.getId(), a.getDevice().getId(), a.getDevice().getName(), a.getLevel(),
                    a.getRaisedAt(), a.getResolvedAt(), a.getResolution());
        }
    }

    public record Snapshot(int lowBelow, Instant serverTime, List<DeviceView> devices,
                           List<AlertView> openAlerts, List<AlertView> recentAlerts) {
    }

    /** One WebSocket message. */
    public record Update(DeviceView device, AlertView alert) {
    }
}
