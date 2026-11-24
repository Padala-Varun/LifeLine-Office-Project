package com.lifeline.battery.dashboard;

import com.lifeline.battery.config.LifelineProperties;
import com.lifeline.battery.dashboard.DashboardViews.AlertView;
import com.lifeline.battery.dashboard.DashboardViews.DeviceView;
import com.lifeline.battery.dashboard.DashboardViews.Snapshot;
import com.lifeline.battery.device.AlertRepository;
import com.lifeline.battery.device.DeviceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;

@RestController
public class DashboardController {

    private static final Limit RECENT = Limit.of(20);

    public record Me(String username) {
    }

    private final DeviceRepository devices;
    private final AlertRepository alerts;
    private final LifelineProperties properties;
    private final Clock clock;

    public DashboardController(DeviceRepository devices, AlertRepository alerts, LifelineProperties properties,
                               Clock clock) {
        this.devices = devices;
        this.alerts = alerts;
        this.properties = properties;
        this.clock = clock;
    }

    /** Username is null when signed out. Also hands the browser its CSRF cookie before login. */
    @GetMapping("/api/auth/me")
    public Me me(Principal principal) {
        return new Me(principal == null ? null : principal.getName());
    }

    @GetMapping("/api/dashboard")
    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(
                properties.alert().lowBelow(),
                clock.instant(),
                devices.findAll().stream().map(DeviceView::of).toList(),
                alerts.findByResolvedAtIsNullOrderByRaisedAtDesc().stream().map(AlertView::of).toList(),
                alerts.findByResolvedAtIsNotNullOrderByResolvedAtDesc(RECENT).stream().map(AlertView::of).toList());
    }
}
