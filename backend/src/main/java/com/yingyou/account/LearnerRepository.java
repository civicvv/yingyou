package com.yingyou.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface LearnerRepository extends JpaRepository<Learner, UUID> {

    Optional<Learner> findByIdAndStatus(UUID id, LearnerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Learner l where l.id = :id")
    Optional<Learner> findByIdForUpdate(@Param("id") UUID id);
}
