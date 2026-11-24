package com.lifeline.battery.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 60)
    private String platform;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    /** Null until the first reading arrives. */
    @Column(name = "battery_level")
    private Integer level;

    private Boolean charging;

    @Column(nullable = false)
    private boolean online;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    protected Device() {
    }

    public Device(UUID id, String name, String platform, String tokenHash, Instant now) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.tokenHash = tokenHash;
        this.online = true;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    /** @return true when the level or charging state differs from the previous reading */
    public boolean applyReading(int level, boolean charging, Instant now) {
        boolean changed = this.level == null || this.level != level || this.charging != charging;
        this.level = level;
        this.charging = charging;
        this.online = true;
        this.lastSeenAt = now;
        return changed;
    }

    public void markOffline() {
        this.online = false;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPlatform() { return platform; }
    public String getTokenHash() { return tokenHash; }
    public Integer getLevel() { return level; }
    public Boolean getCharging() { return charging; }
    public boolean isOnline() { return online; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
