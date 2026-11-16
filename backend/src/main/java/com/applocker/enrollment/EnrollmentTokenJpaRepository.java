package com.applocker.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EnrollmentTokenJpaRepository extends JpaRepository<EnrollmentTokenEntity, UUID> {

    Optional<EnrollmentTokenEntity> findByLabel(String label);
}
