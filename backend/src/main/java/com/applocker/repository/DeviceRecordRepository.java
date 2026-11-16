package com.applocker.repository;

import com.applocker.model.DeviceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRecordRepository extends JpaRepository<DeviceRecord, UUID> {

    Optional<DeviceRecord> findByWindowsDeviceId(String windowsDeviceId);

    List<DeviceRecord> findAllByOrderByEnrolledAtAsc();
}
