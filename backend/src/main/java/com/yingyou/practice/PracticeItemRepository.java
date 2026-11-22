package com.yingyou.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeItemRepository extends JpaRepository<PracticeItem, UUID> {

    List<PracticeItem> findBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<PracticeItem> findBySessionIdAndPosition(UUID sessionId, int position);
}
