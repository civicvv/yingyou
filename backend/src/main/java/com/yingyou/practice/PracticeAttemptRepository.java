package com.yingyou.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, UUID> {

    long countByPracticeItemId(UUID practiceItemId);

    List<PracticeAttempt> findByPracticeItemIdOrderByOrdinalAsc(UUID practiceItemId);
}
