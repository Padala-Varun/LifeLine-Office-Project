package com.lifeline.battery.device;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    /** Row lock, so two tabs on one device cannot both raise the same alert. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.id = :id")
    Optional<Device> findForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Device d where d.online = true and d.lastSeenAt < :cutoff")
    List<Device> findSilentForUpdate(Instant cutoff);
}
