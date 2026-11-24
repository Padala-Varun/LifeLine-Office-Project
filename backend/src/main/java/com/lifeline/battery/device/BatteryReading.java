package com.lifeline.battery.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** History row. Written only when the level or charging state changes, not on every heartbeat. */
@Entity
@Table(name = "battery_readings")
public class BatteryReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "battery_level", nullable = false)
    private int level;

    @Column(nullable = false)
    private boolean charging;

    @Column(nullable = false)
    private Instant recordedAt;

    protected BatteryReading() {
    }

    public BatteryReading(Device device, int level, boolean charging, Instant recordedAt) {
        this.device = device;
        this.level = level;
        this.charging = charging;
        this.recordedAt = recordedAt;
    }
}
