package com.applocker.repository;

import com.applocker.model.DeviceMdmRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceMdmRepository extends JpaRepository<DeviceMdmRecord, UUID> {
}
