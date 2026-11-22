package com.yingyou.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 登录成功且存在有效访客会话时，把访客练习事实并入账号身份。
 * 先按 UUID 顺序锁定两个身份行，再关闭重复的进行中会话并转移事实。
 */
@Service
public class GuestRecordMigrationService {

    private final LearnerRepository learnerRepository;
    private final JdbcClient jdbcClient;

    public GuestRecordMigrationService(LearnerRepository learnerRepository, JdbcClient jdbcClient) {
        this.learnerRepository = learnerRepository;
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public boolean migrate(UUID guestLearnerId, UUID accountLearnerId) {
        if (guestLearnerId == null || guestLearnerId.equals(accountLearnerId)) {
            return false;
        }

        List<UUID> orderedIds = List.of(guestLearnerId, accountLearnerId).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        for (UUID id : orderedIds) {
            learnerRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new IllegalStateException("迁移身份不存在：" + id));
        }

        Optional<Learner> guest = learnerRepository.findById(guestLearnerId);
        if (guest.isEmpty()
                || guest.get().getKind() != LearnerKind.GUEST
                || guest.get().getStatus() != LearnerStatus.ACTIVE) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        closeDuplicateOpenSessions(guestLearnerId, accountLearnerId, now);
        transferSessions(guestLearnerId, accountLearnerId);
        transferSessionRequests(guestLearnerId, accountLearnerId);
        rebuildLessonProgress(guestLearnerId, accountLearnerId);
        transferAssessmentAndPreferences(guestLearnerId, accountLearnerId);

        jdbcClient.sql("""
                        update learner
                        set status = 'MIGRATED', migrated_to_learner_id = :accountId
                        where id = :guestId
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
        return true;
    }

    private void closeDuplicateOpenSessions(UUID guestLearnerId, UUID accountLearnerId, OffsetDateTime now) {
        jdbcClient.sql("""
                        with paired as (
                            select s.id as guest_id, s.updated_at as guest_updated,
                                   a.id as account_id, a.updated_at as account_updated
                            from practice_session s
                            join practice_session a
                              on a.learner_id = :accountId
                             and a.lesson_id = s.lesson_id
                             and a.scenario = 'LESSON'
                             and a.status = 'OPEN'
                            where s.learner_id = :guestId
                              and s.scenario = 'LESSON'
                              and s.status = 'OPEN'
                        )
                        update practice_session p
                        set status = 'ABANDONED', updated_at = :now
                        from paired pr
                        where p.id = case
                                when pr.guest_updated >= pr.account_updated then pr.account_id
                                else pr.guest_id
                            end
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .param("now", now)
                .update();
    }

    private void transferSessions(UUID guestLearnerId, UUID accountLearnerId) {
        jdbcClient.sql("""
                        update practice_session
                        set learner_id = :accountId
                        where learner_id = :guestId
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
    }

    private void transferSessionRequests(UUID guestLearnerId, UUID accountLearnerId) {
        jdbcClient.sql("""
                        delete from practice_session_request g
                        where g.learner_id = :guestId
                          and exists (
                              select 1 from practice_session_request a
                              where a.learner_id = :accountId and a.request_id = g.request_id
                          )
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
        jdbcClient.sql("""
                        update practice_session_request
                        set learner_id = :accountId
                        where learner_id = :guestId
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
    }

    /**
     * 按会话事实重算课节进度，合并账号原有进度。
     */
    private void rebuildLessonProgress(UUID guestLearnerId, UUID accountLearnerId) {
        jdbcClient.sql("""
                        delete from learner_lesson_progress
                        where learner_id = :guestId
                        """)
                .param("guestId", guestLearnerId)
                .update();

        jdbcClient.sql("""
                        insert into learner_lesson_progress
                            (learner_id, lesson_id, last_active_session_id, last_completed_session_id,
                             first_completed_at, last_practiced_at)
                        select :accountId,
                               lesson_id,
                               (array_agg(id order by updated_at desc))[1],
                               (array_agg(id order by completed_at desc nulls last)
                                   filter (where status = 'COMPLETED'))[1],
                               min(completed_at) filter (where status = 'COMPLETED'),
                               max(updated_at)
                        from practice_session
                        where learner_id = :accountId
                          and scenario = 'LESSON'
                          and lesson_id is not null
                        group by lesson_id
                        on conflict (learner_id, lesson_id) do update
                        set last_active_session_id = excluded.last_active_session_id,
                            last_completed_session_id = excluded.last_completed_session_id,
                            first_completed_at = excluded.first_completed_at,
                            last_practiced_at = excluded.last_practiced_at
                        """)
                .param("accountId", accountLearnerId)
                .update();
    }

    /**
     * 迁移测评结果与定制偏好。目标账号已有的设置优先；重复进行中的测评只保留账号侧。
     */
    private void transferAssessmentAndPreferences(UUID guestLearnerId, UUID accountLearnerId) {
        jdbcClient.sql("""
                        delete from assessment_request
                        where learner_id = :guestId
                          and session_id in (
                              select id from assessment_session
                              where learner_id = :guestId and status = 'OPEN'
                          )
                          and exists (
                              select 1 from assessment_session
                              where learner_id = :accountId and status = 'OPEN'
                          )
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
        jdbcClient.sql("""
                        delete from assessment_session
                        where learner_id = :guestId and status = 'OPEN'
                          and exists (
                              select 1 from assessment_session
                              where learner_id = :accountId and status = 'OPEN'
                          )
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();

        for (String table : List.of("assessment_session", "assessment_result")) {
            jdbcClient.sql("update " + table + " set learner_id = :accountId where learner_id = :guestId")
                    .param("accountId", accountLearnerId)
                    .param("guestId", guestLearnerId)
                    .update();
        }

        jdbcClient.sql("""
                        delete from learner_exam_preference g
                        where g.learner_id = :guestId
                          and exists (
                              select 1 from learner_exam_preference a where a.learner_id = :accountId
                          )
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
        jdbcClient.sql("update learner_exam_preference set learner_id = :accountId where learner_id = :guestId")
                .param("accountId", accountLearnerId)
                .param("guestId", guestLearnerId)
                .update();

        jdbcClient.sql("""
                        delete from learner_track_placement g
                        where g.learner_id = :guestId
                          and exists (
                              select 1 from learner_track_placement a
                              where a.learner_id = :accountId and a.exam_track = g.exam_track
                          )
                        """)
                .param("guestId", guestLearnerId)
                .param("accountId", accountLearnerId)
                .update();
        jdbcClient.sql("update learner_track_placement set learner_id = :accountId where learner_id = :guestId")
                .param("accountId", accountLearnerId)
                .param("guestId", guestLearnerId)
                .update();

        for (String table : List.of("personalized_request", "preference_request", "assessment_request")) {
            jdbcClient.sql("delete from " + table + " g where g.learner_id = :guestId "
                    + "and exists (select 1 from " + table + " a where a.learner_id = :accountId "
                    + "and a.request_id = g.request_id)")
                    .param("guestId", guestLearnerId)
                    .param("accountId", accountLearnerId)
                    .update();
            jdbcClient.sql("update " + table + " set learner_id = :accountId where learner_id = :guestId")
                    .param("accountId", accountLearnerId)
                    .param("guestId", guestLearnerId)
                    .update();
        }
    }
}
