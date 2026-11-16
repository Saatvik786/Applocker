package com.applocker.repository;

import com.applocker.model.PolicyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PolicyProfileRepository extends JpaRepository<PolicyProfile, UUID> {
}
