package com.yingyou.practice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeSessionRequestRepository
        extends JpaRepository<PracticeSessionRequest, PracticeSessionRequestId> {
}
