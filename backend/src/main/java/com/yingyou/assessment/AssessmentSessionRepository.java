package com.yingyou.assessment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssessmentSession s where s.id = :id")
    Optional<AssessmentSession> findByIdForUpdate(@Param("id") UUID id);

    Optional<AssessmentSession> findByLearnerIdAndStatus(UUID learnerId, String status);
}
