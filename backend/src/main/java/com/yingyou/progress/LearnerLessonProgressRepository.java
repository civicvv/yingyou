package com.yingyou.progress;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class LearnerLessonProgressRepository {

    private final JdbcClient jdbcClient;

    public LearnerLessonProgressRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record ProgressRow(
            String lessonId,
            UUID lastActiveSessionId,
            UUID lastCompletedSessionId,
            Instant firstCompletedAt,
            Instant lastPracticedAt
    ) {
        public boolean completed() {
            return lastCompletedSessionId != null;
        }
    }

    public void touch(UUID learnerId, String lessonId, UUID sessionId, Instant now) {
        jdbcClient.sql("""
                        insert into learner_lesson_progress
                            (learner_id, lesson_id, last_active_session_id, last_practiced_at)
                        values (:learnerId, :lessonId, :sessionId, :now)
                        on conflict (learner_id, lesson_id) do update
                        set last_active_session_id = excluded.last_active_session_id,
                            last_practiced_at = excluded.last_practiced_at
                        """)
                .param("learnerId", learnerId)
                .param("lessonId", lessonId)
                .param("sessionId", sessionId)
                .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .update();
    }

    public void markCompleted(UUID learnerId, String lessonId, UUID sessionId, Instant now) {
        jdbcClient.sql("""
                        insert into learner_lesson_progress
                            (learner_id, lesson_id, last_active_session_id, last_completed_session_id,
                             first_completed_at, last_practiced_at)
                        values (:learnerId, :lessonId, :sessionId, :sessionId, :now, :now)
                        on conflict (learner_id, lesson_id) do update
                        set last_active_session_id = excluded.last_active_session_id,
                            last_completed_session_id = excluded.last_completed_session_id,
                            first_completed_at = coalesce(learner_lesson_progress.first_completed_at, excluded.first_completed_at),
                            last_practiced_at = excluded.last_practiced_at
                        """)
                .param("learnerId", learnerId)
                .param("lessonId", lessonId)
                .param("sessionId", sessionId)
                .param("now", OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC))
                .update();
    }

    public List<ProgressRow> findByLearner(UUID learnerId) {
        return jdbcClient.sql("""
                        select lesson_id, last_active_session_id, last_completed_session_id,
                               first_completed_at, last_practiced_at
                        from learner_lesson_progress
                        where learner_id = :learnerId
                        order by last_practiced_at desc
                        """)
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    public Map<String, ProgressRow> findByLearnerAndLessonIds(UUID learnerId, List<String> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return jdbcClient.sql("""
                        select lesson_id, last_active_session_id, last_completed_session_id,
                               first_completed_at, last_practiced_at
                        from learner_lesson_progress
                        where learner_id = :learnerId
                          and lesson_id = any(:lessonIds)
                        """)
                .param("learnerId", learnerId)
                .param("lessonIds", lessonIds.toArray(new String[0]))
                .query((rs, rowNum) -> map(rs))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(ProgressRow::lessonId, row -> row, (first, second) -> first));
    }

    private ProgressRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProgressRow(
                rs.getString("lesson_id"),
                rs.getObject("last_active_session_id", UUID.class),
                rs.getObject("last_completed_session_id", UUID.class),
                toInstant(rs.getObject("first_completed_at", OffsetDateTime.class)),
                toInstant(rs.getObject("last_practiced_at", OffsetDateTime.class)));
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
