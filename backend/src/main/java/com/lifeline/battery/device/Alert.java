package com.lifeline.battery.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    public enum Resolution { CHARGING, RECOVERED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    /** Charge at the moment the alert was raised. */
    @Column(name = "battery_level", nullable = false)
    private int level;

    @Column(nullable = false)
    private Instant raisedAt;

    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Resolution resolution;

    protected Alert() {
    }

    public Alert(Device device, int level, Instant raisedAt) {
        this.device = device;
        this.level = level;
        this.raisedAt = raisedAt;
    }

    public void resolve(Resolution resolution, Instant now) {
        this.resolution = resolution;
        this.resolvedAt = now;
    }

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public int getLevel() { return level; }
    public Instant getRaisedAt() { return raisedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Resolution getResolution() { return resolution; }
}
