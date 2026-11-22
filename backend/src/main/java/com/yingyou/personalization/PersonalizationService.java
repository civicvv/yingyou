package com.yingyou.personalization;

import com.yingyou.account.Learner;
import com.yingyou.account.LearnerRepository;
import com.yingyou.assessment.AssessmentService;
import com.yingyou.personalization.PersonalizationRequests.CreatePersonalizedSessionRequest;
import com.yingyou.personalization.PersonalizationRequests.UpdatePreferencesRequest;
import com.yingyou.personalization.PersonalizationViews.BatchView;
import com.yingyou.personalization.PersonalizationViews.PreferencesView;
import com.yingyou.practice.PracticeService;
import com.yingyou.practice.PracticeSession;
import com.yingyou.practice.PracticeSessionView;
import com.yingyou.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PersonalizationService {

    public static final String RULE_VERSION = "v1";
    private static final String DEFAULT_TRACK = "GENERAL";
    private static final int BATCH_SIZE = 10;
    private static final String[] DIFFICULTIES = {"D1", "D2", "D3", "D4"};

    private final JdbcClient jdbcClient;
    private final LearnerRepository learnerRepository;
    private final AssessmentService assessmentService;
    private final PracticeService practiceService;
    private final ObjectMapper objectMapper;

    public PersonalizationService(JdbcClient jdbcClient,
                                  LearnerRepository learnerRepository,
                                  AssessmentService assessmentService,
                                  PracticeService practiceService,
                                  ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.learnerRepository = learnerRepository;
        this.assessmentService = assessmentService;
        this.practiceService = practiceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PreferencesView preferences(UUID learnerId) {
        String examTrack = preferenceTrack(learnerId);
        String vocabBand = assessmentService.latestVocabBand(learnerId).orElse("V1");
        Placement placement = ensurePlacementView(learnerId, examTrack, vocabBand);
        Instant lastAssessment = latestAssessmentAt(learnerId).orElse(null);
        return new PreferencesView(examTrack, vocabBand, placement.autoDifficulty(),
                placement.manualDifficulty(), effective(placement),
                preferenceRevision(learnerId), lastAssessment);
    }

    @Transactional
    public PreferencesView updatePreferences(UUID learnerId, UpdatePreferencesRequest request) {
        learnerRepository.findByIdForUpdate(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习者身份"));

        String digest = digest(request.actionDigest());
        Optional<PreferencesView> previous = storedPreferenceResponse(learnerId, request.requestId(), digest);
        if (previous.isPresent()) {
            return previous.get();
        }

        String currentTrack = preferenceTrack(learnerId);
        long currentRevision = preferenceRevision(learnerId);
        if (request.revision() != currentRevision) {
            throw conflict("REVISION_CONFLICT", "偏好已更新，请重新读取");
        }
        String examTrack = normalizeTrack(request.examTrack() == null ? currentTrack : request.examTrack());
        String mode = request.difficultyMode() == null ? "AUTO" : request.difficultyMode().toUpperCase(Locale.ROOT);
        if (!mode.equals("AUTO") && !mode.equals("MANUAL")) {
            throw ApiException.badRequest("INVALID_DIFFICULTY_MODE", "难度模式只能是 AUTO 或 MANUAL");
        }
        String manual = request.manualDifficulty() == null || request.manualDifficulty().isBlank()
                ? null : request.manualDifficulty().toUpperCase(Locale.ROOT);
        if (manual != null && !List.of(DIFFICULTIES).contains(manual)) {
            throw ApiException.badRequest("INVALID_DIFFICULTY", "难度只能是 D1 至 D4");
        }
        if (mode.equals("MANUAL") && manual == null) {
            throw ApiException.badRequest("MANUAL_DIFFICULTY_REQUIRED", "手动模式需要指定难度");
        }

        jdbcClient.sql("""
                        update learner_exam_preference
                        set exam_track = :examTrack, revision = revision + 1, updated_at = :now
                        where learner_id = :learnerId
                        """)
                .param("examTrack", examTrack)
                .param("now", utc(Instant.now()))
                .param("learnerId", learnerId)
                .update();

        String vocabBand = assessmentService.latestVocabBand(learnerId).orElse("V1");
        Placement placement = ensurePlacement(learnerId, examTrack, vocabBand);
        jdbcClient.sql("""
                        insert into learner_track_placement
                            (learner_id, exam_track, auto_difficulty, manual_difficulty, rule_version, updated_at)
                        values (:learnerId, :examTrack, :auto, :manual, :rule, :now)
                        on conflict (learner_id, exam_track) do update
                        set manual_difficulty = excluded.manual_difficulty,
                            updated_at = excluded.updated_at
                        """)
                .param("learnerId", learnerId)
                .param("examTrack", examTrack)
                .param("auto", placement.autoDifficulty())
                .param("manual", mode.equals("MANUAL") ? manual : null)
                .param("rule", RULE_VERSION)
                .param("now", utc(Instant.now()))
                .update();

        PreferencesView result = preferences(learnerId);
        jdbcClient.sql("""
                        insert into preference_request
                            (learner_id, request_id, request_digest, response_body, created_at)
                        values (:learnerId, :requestId, :digest, :body, :now)
                        """)
                .param("learnerId", learnerId)
                .param("requestId", request.requestId())
                .param("digest", digest)
                .param("body", objectMapper.writeValueAsString(result))
                .param("now", utc(Instant.now()))
                .update();
        return result;
    }

    @Transactional
    public BatchView createSession(UUID learnerId, CreatePersonalizedSessionRequest request) {
        learnerRepository.findByIdForUpdate(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习者身份"));

        String examTrack = normalizeTrack(request.examTrack() == null
                ? preferenceTrack(learnerId) : request.examTrack());
        String vocabBand = assessmentService.latestVocabBand(learnerId).orElse("V1");
        Placement placement = evaluatePlacement(learnerId, examTrack, vocabBand);
        String baseline = effective(placement);
        UUID assessmentId = assessmentService.latestAssessmentId(learnerId).orElse(null);

        String digest = digest(examTrack + "|" + assessmentId + "|" + vocabBand + "|" + baseline);
        Optional<UUID> existing = storedPersonalizedSession(learnerId, request.requestId(), digest);
        if (existing.isPresent()) {
            PracticeSession session = practiceService.requireSession(existing.get());
            return batchView(practiceService.getSession(learnerId, session.getId()), examTrack,
                    vocabBand, baseline, null, null);
        }

        Selection selection = selectSentences(learnerId, examTrack, vocabBand, baseline);
        if (selection.versions().size() < 5) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_PRACTICE_ITEMS",
                    "当前目标可用题量不足，请先练习已发布课节");
        }

        PracticeSessionView sessionView = practiceService.createPersonalizedSession(learnerId,
                new PracticeService.PersonalizedSessionCommand(
                        request.requestId(), examTrack, assessmentId, vocabBand, baseline,
                        RULE_VERSION, selection.fallbackReason(), selection.versions()));

        jdbcClient.sql("""
                        update practice_selection
                        set next_difficulty_suggestion = :suggestion
                        where practice_session_id = :sessionId
                        """)
                .param("suggestion", placement.autoDifficulty())
                .param("sessionId", sessionView.id())
                .update();

        jdbcClient.sql("""
                        insert into personalized_request
                            (learner_id, request_id, request_digest, session_id, created_at)
                        values (:learnerId, :requestId, :digest, :sessionId, :now)
                        """)
                .param("learnerId", learnerId)
                .param("requestId", request.requestId())
                .param("digest", digest)
                .param("sessionId", sessionView.id())
                .param("now", utc(Instant.now()))
                .update();

        return batchView(sessionView, examTrack, vocabBand, baseline,
                selection.fallbackReason(), selection.reasons());
    }

    private BatchView batchView(PracticeSessionView session, String examTrack, String vocabBand,
                                String baseline, String fallbackReason, List<String> reasons) {
        return new BatchView(session, examTrack, vocabBand, baseline, session.total(),
                reasons == null ? List.of("按目标与档位选题", "优先目标路径题目") : reasons,
                fallbackReason);
    }

    private Placement evaluatePlacement(UUID learnerId, String examTrack, String vocabBand) {
        Placement placement = ensurePlacement(learnerId, examTrack, vocabBand);
        String currentAuto = placement.autoDifficulty();
        List<CompletedBatch> batches = jdbcClient.sql("""
                        select ps.id, ps.completed_at, sel.baseline_difficulty
                        from practice_session ps
                        join practice_selection sel on sel.practice_session_id = ps.id
                        where ps.learner_id = :learnerId and ps.exam_track = :examTrack
                          and ps.scenario = 'PERSONALIZED' and ps.status = 'COMPLETED'
                          and sel.baseline_difficulty = :baseline
                        order by ps.completed_at desc, ps.id desc
                        limit 2
                        """)
                .param("learnerId", learnerId)
                .param("examTrack", examTrack)
                .param("baseline", currentAuto)
                .query((rs, rowNum) -> new CompletedBatch(
                        UUID.fromString(rs.getString("id")),
                        rs.getObject("completed_at", OffsetDateTime.class).toInstant()))
                .list();
        if (batches.isEmpty()) {
            return placement;
        }

        Map<String, Boolean> firstTry = new LinkedHashMap<>();
        for (CompletedBatch batch : batches) {
            jdbcClient.sql("""
                            select sv.sentence_id as stableId,
                                   max(case when a.ordinal = 1 and a.is_correct and not a.assisted
                                            then 1 else 0 end) as firstOk
                            from practice_item pi
                            join attempt a on a.practice_item_id = pi.id
                            join sentence_version sv on sv.id = pi.sentence_version_id
                            where pi.session_id = :sessionId
                            group by sv.sentence_id
                            """)
                    .param("sessionId", batch.sessionId())
                    .query((rs, rowNum) -> {
                        firstTry.putIfAbsent(rs.getString("stableId"), rs.getInt("firstOk") == 1);
                        return rs.getString("stableId");
                    })
                    .list();
            if (firstTry.size() >= 10) {
                break;
            }
        }
        if (firstTry.size() < 10) {
            return placement;
        }
        long correct = firstTry.values().stream().filter(Boolean::booleanValue).count();
        double rate = correct / 10.0;
        String next = currentAuto;
        if (rate >= 0.8) {
            next = raise(currentAuto);
        } else if (rate < 0.55) {
            next = lower(currentAuto);
        }
        if (!next.equals(currentAuto)) {
            jdbcClient.sql("""
                            update learner_track_placement
                            set auto_difficulty = :next,
                                last_evaluated_session_id = :sessionId,
                                rule_version = :rule,
                                updated_at = :now
                            where learner_id = :learnerId and exam_track = :examTrack
                            """)
                    .param("next", next)
                    .param("sessionId", batches.get(0).sessionId())
                    .param("rule", RULE_VERSION)
                    .param("now", utc(Instant.now()))
                    .param("learnerId", learnerId)
                    .param("examTrack", examTrack)
                    .update();
            return new Placement(next, placement.manualDifficulty());
        }
        return placement;
    }

    private Selection selectSentences(UUID learnerId, String examTrack, String vocabBand, String baseline) {
        List<Candidate> candidates = jdbcClient.sql("""
                        select distinct sv.id, sv.sentence_id as stableId,
                               sv.exam_tracks::text as examTracks, sv.difficulty,
                               sv.topic_code as topicCode, sv.primary_skill as primarySkill,
                               sv.min_vocab_band as minVocabBand
                        from sentence_version sv
                        join lesson_version_item lvi on lvi.sentence_version_id = sv.id
                        join lesson_version lv on lv.id = lvi.lesson_version_id and lv.published_at is not null
                        join lesson l on l.id = lv.lesson_id and l.published_version_id = lv.id
                        join course c on c.id = l.course_id and c.status = 'PUBLISHED'
                        """)
                .query((rs, rowNum) -> new Candidate(
                        rs.getString("id"),
                        rs.getString("stableId"),
                        objectMapper.readValue(rs.getString("examTracks"), String[].class),
                        rs.getString("difficulty"),
                        rs.getString("topicCode"),
                        rs.getString("primarySkill"),
                        rs.getString("minVocabBand")))
                .list();

        Set<String> recent = new HashSet<>(jdbcClient.sql("""
                        select distinct sv.sentence_id
                        from practice_session ps
                        join practice_item pi on pi.session_id = ps.id
                        join sentence_version sv on sv.id = pi.sentence_version_id
                        where ps.learner_id = :learnerId and ps.created_at > :since
                        """)
                .param("learnerId", learnerId)
                .param("since", utc(Instant.now().minus(7, ChronoUnit.DAYS)))
                .query((rs, rowNum) -> rs.getString(1))
                .list());

        List<Candidate> available = candidates.stream()
                .filter(candidate -> supportsTrack(candidate, examTrack))
                .sorted(Comparator.comparing((Candidate candidate) -> isTargetTrack(candidate, examTrack) ? 0 : 1)
                        .thenComparing(candidate -> candidate.id()))
                .toList();

        int current = index(baseline);
        int bandLimit = bandIndex(vocabBand);
        int[] slotDifficulties = new int[BATCH_SIZE];
        Arrays.fill(slotDifficulties, current);
        for (int index = 0; index < 3; index++) {
            slotDifficulties[6 + index] = Math.max(0, current - 1);
        }
        slotDifficulties[9] = Math.min(3, current + 1);

        List<String> versions = new ArrayList<>();
        Set<String> usedStable = new HashSet<>();
        Set<String> usedTopics = new HashSet<>();
        boolean relaxed = false;
        for (int slot = 0; slot < BATCH_SIZE; slot++) {
            int difficulty = slotDifficulties[slot];
            int maxBand = slot == 9 ? Math.min(4, bandLimit + 1) : bandLimit;
            Candidate chosen = pick(available, difficulty, maxBand, usedStable, usedTopics, recent, relaxed);
            if (chosen == null && !relaxed && !recent.isEmpty()) {
                relaxed = true;
                chosen = pick(available, difficulty, maxBand, usedStable, usedTopics, recent, true);
            }
            if (chosen == null) {
                continue;
            }
            versions.add(chosen.id());
            usedStable.add(chosen.stableId());
            usedTopics.add(chosen.topicCode());
        }
        List<String> reasons = new ArrayList<>(List.of("按目标与难度槽位选题", "优先目标路径题目"));
        if (!usedTopics.isEmpty()) {
            reasons.add("覆盖 " + usedTopics.size() + " 个主题");
        }
        return new Selection(versions, relaxed ? "RECENT_DEDUP_RELAXED" : null, reasons);
    }

    private Candidate pick(List<Candidate> candidates, int difficultyIndex, int maxBand,
                           Set<String> usedStable, Set<String> usedTopics, Set<String> recent, boolean relaxed) {
        for (Candidate candidate : candidates) {
            if (usedStable.contains(candidate.stableId()) || !difficulty(candidate).equals(DIFFICULTIES[difficultyIndex])) {
                continue;
            }
            if (bandIndex(candidate.minVocabBand()) > maxBand) {
                continue;
            }
            if (!relaxed && recent.contains(candidate.stableId())) {
                continue;
            }
            if (usedTopics.contains(candidate.topicCode())) {
                continue;
            }
            return candidate;
        }
        for (Candidate candidate : candidates) {
            if (usedStable.contains(candidate.stableId()) || !difficulty(candidate).equals(DIFFICULTIES[difficultyIndex])) {
                continue;
            }
            if (bandIndex(candidate.minVocabBand()) > maxBand) {
                continue;
            }
            if (!relaxed && recent.contains(candidate.stableId())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean supportsTrack(Candidate candidate, String examTrack) {
        return Arrays.asList(candidate.examTracks()).contains(examTrack)
                || Arrays.asList(candidate.examTracks()).contains(DEFAULT_TRACK);
    }

    private boolean isTargetTrack(Candidate candidate, String examTrack) {
        return Arrays.asList(candidate.examTracks()).contains(examTrack)
                && !examTrack.equals(DEFAULT_TRACK);
    }

    private String difficulty(Candidate candidate) {
        return candidate.difficulty().toUpperCase(Locale.ROOT);
    }

    private int bandIndex(String band) {
        return Math.max(1, Math.min(4, Integer.parseInt(band.substring(1))));
    }

    private int index(String difficulty) {
        int index = Arrays.asList(DIFFICULTIES).indexOf(difficulty);
        return index < 0 ? 0 : index;
    }

    private String raise(String difficulty) {
        return DIFFICULTIES[Math.min(3, index(difficulty) + 1)];
    }

    private String lower(String difficulty) {
        return DIFFICULTIES[Math.max(0, index(difficulty) - 1)];
    }

    private String effective(Placement placement) {
        return placement.manualDifficulty() == null ? placement.autoDifficulty() : placement.manualDifficulty();
    }

    private Placement ensurePlacementView(UUID learnerId, String examTrack, String vocabBand) {
        Optional<Placement> existing = jdbcClient.sql("""
                        select auto_difficulty, manual_difficulty from learner_track_placement
                        where learner_id = :learnerId and exam_track = :examTrack
                        """)
                .param("learnerId", learnerId)
                .param("examTrack", examTrack)
                .query((rs, rowNum) -> new Placement(
                        rs.getString("auto_difficulty"), rs.getString("manual_difficulty")))
                .stream()
                .findFirst();
        return existing.orElseGet(() -> defaultPlacement(vocabBand));
    }

    private Placement ensurePlacement(UUID learnerId, String examTrack, String vocabBand) {
        Placement placement = ensurePlacementView(learnerId, examTrack, vocabBand);
        jdbcClient.sql("""
                        insert into learner_track_placement
                            (learner_id, exam_track, auto_difficulty, manual_difficulty, rule_version, updated_at)
                        values (:learnerId, :examTrack, :auto, :manual, :rule, :now)
                        on conflict (learner_id, exam_track) do nothing
                        """)
                .param("learnerId", learnerId)
                .param("examTrack", examTrack)
                .param("auto", placement.autoDifficulty())
                .param("manual", placement.manualDifficulty())
                .param("rule", RULE_VERSION)
                .param("now", utc(Instant.now()))
                .update();
        return placement;
    }

    private Placement defaultPlacement(String vocabBand) {
        return new Placement(switch (vocabBand) {
            case "V2", "V3" -> "D2";
            case "V4" -> "D3";
            default -> "D1";
        }, null);
    }

    private String preferenceTrack(UUID learnerId) {
        return jdbcClient.sql("select exam_track from learner_exam_preference where learner_id = :learnerId")
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> rs.getString("exam_track"))
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    jdbcClient.sql("""
                                    insert into learner_exam_preference (learner_id, exam_track, revision, updated_at)
                                    values (:learnerId, :track, 0, :now)
                                    on conflict (learner_id) do nothing
                                    """)
                            .param("learnerId", learnerId)
                            .param("track", DEFAULT_TRACK)
                            .param("now", utc(Instant.now()))
                            .update();
                    return DEFAULT_TRACK;
                });
    }

    private long preferenceRevision(UUID learnerId) {
        return jdbcClient.sql("select revision from learner_exam_preference where learner_id = :learnerId")
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> rs.getLong("revision"))
                .stream()
                .findFirst()
                .orElse(0L);
    }

    private Optional<Instant> latestAssessmentAt(UUID learnerId) {
        return jdbcClient.sql("""
                        select completed_at from assessment_result
                        where learner_id = :learnerId
                        order by completed_at desc, session_id desc limit 1
                        """)
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> rs.getObject("completed_at", OffsetDateTime.class).toInstant())
                .stream()
                .findFirst();
    }

    private Optional<PreferencesView> storedPreferenceResponse(UUID learnerId, UUID requestId, String digest) {
        return jdbcClient.sql("""
                        select request_digest, response_body from preference_request
                        where learner_id = :learnerId and request_id = :requestId
                        """)
                .param("learnerId", learnerId)
                .param("requestId", requestId)
                .query((rs, rowNum) -> {
                    if (!digest.equals(rs.getString("request_digest"))) {
                        throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同请求内容");
                    }
                    return objectMapper.readValue(rs.getString("response_body"), PreferencesView.class);
                })
                .stream()
                .findFirst();
    }

    private Optional<UUID> storedPersonalizedSession(UUID learnerId, UUID requestId, String digest) {
        return jdbcClient.sql("""
                        select request_digest, session_id from personalized_request
                        where learner_id = :learnerId and request_id = :requestId
                        """)
                .param("learnerId", learnerId)
                .param("requestId", requestId)
                .query((rs, rowNum) -> {
                    if (!digest.equals(rs.getString("request_digest"))) {
                        throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同请求内容");
                    }
                    return UUID.fromString(rs.getString("session_id"));
                })
                .stream()
                .findFirst();
    }

    private String normalizeTrack(String track) {
        String value = track == null || track.isBlank() ? DEFAULT_TRACK : track.toUpperCase(Locale.ROOT);
        if (!List.of("CET4", "CET6", "IELTS_ACADEMIC", "TOEFL_IBT", DEFAULT_TRACK).contains(value)) {
            throw ApiException.badRequest("INVALID_EXAM_TRACK", "不支持的考试目标");
        }
        return value;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record Placement(String autoDifficulty, String manualDifficulty) {
    }

    private record CompletedBatch(UUID sessionId, Instant completedAt) {
    }

    private record Candidate(String id, String stableId, String[] examTracks, String difficulty,
                             String topicCode, String primarySkill, String minVocabBand) {
    }

    private record Selection(List<String> versions, String fallbackReason, List<String> reasons) {
    }
}
