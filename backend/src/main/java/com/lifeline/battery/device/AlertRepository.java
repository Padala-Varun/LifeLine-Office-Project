package com.lifeline.battery.device;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findFirstByDeviceAndResolvedAtIsNull(Device device);

    @EntityGraph(attributePaths = "device")
    List<Alert> findByResolvedAtIsNullOrderByRaisedAtDesc();

    @EntityGraph(attributePaths = "device")
    List<Alert> findByResolvedAtIsNotNullOrderByResolvedAtDesc(Limit limit);
}
