package com.yingyou.practice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSession s where s.id = :id")
    Optional<PracticeSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select s from PracticeSession s
            where s.learnerId = :learnerId and s.lessonId = :lessonId
              and s.scenario = com.yingyou.practice.PracticeScenario.LESSON
              and s.status = com.yingyou.practice.PracticeSessionStatus.OPEN
            """)
    Optional<PracticeSession> findOpenLessonSession(@Param("learnerId") UUID learnerId,
                                                    @Param("lessonId") String lessonId);

    @Query("""
            select s from PracticeSession s
            where s.learnerId = :learnerId
              and s.scenario = com.yingyou.practice.PracticeScenario.LESSON
              and s.status = com.yingyou.practice.PracticeSessionStatus.OPEN
            """)
    List<PracticeSession> findOpenLessonSessions(@Param("learnerId") UUID learnerId);

    @Query("""
            select s from PracticeSession s
            where s.learnerId = :learnerId and s.examTrack = :examTrack
              and s.scenario = com.yingyou.practice.PracticeScenario.PERSONALIZED
              and s.status = com.yingyou.practice.PracticeSessionStatus.OPEN
            """)
    Optional<PracticeSession> findOpenPersonalizedSession(@Param("learnerId") UUID learnerId,
                                                          @Param("examTrack") String examTrack);
}
