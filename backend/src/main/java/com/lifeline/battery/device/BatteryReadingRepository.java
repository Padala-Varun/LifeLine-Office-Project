package com.lifeline.battery.device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatteryReadingRepository extends JpaRepository<BatteryReading, Long> {
}
