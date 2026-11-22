package com.yingyou.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountSessionRepository extends JpaRepository<AccountSession, String> {

    List<AccountSession> findByLearnerId(java.util.UUID learnerId);

    Optional<AccountSession> findTopByLearnerIdOrderByCreatedAtDesc(java.util.UUID learnerId);

    void deleteByLearnerId(java.util.UUID learnerId);
}
