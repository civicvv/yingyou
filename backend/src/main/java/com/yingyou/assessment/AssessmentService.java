package com.yingyou.assessment;

import com.yingyou.account.Learner;
import com.yingyou.account.LearnerRepository;
import com.yingyou.assessment.AssessmentRequests.CreateAssessmentRequest;
import com.yingyou.assessment.AssessmentRequests.SubmitAnswerRequest;
import com.yingyou.assessment.AssessmentViews.BandStatView;
import com.yingyou.assessment.AssessmentViews.HistoryView;
import com.yingyou.assessment.AssessmentViews.ItemView;
import com.yingyou.assessment.AssessmentViews.OptionView;
import com.yingyou.assessment.AssessmentViews.ResultView;
import com.yingyou.assessment.AssessmentViews.SessionView;
import com.yingyou.assessment.VocabItemRepository.VocabItemRow;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AssessmentService {

    private final AssessmentSessionRepository sessionRepository;
    private final VocabItemRepository itemRepository;
    private final LearnerRepository learnerRepository;
    private final JdbcClient jdbcClient;
    private final VocabScoring scoring;
    private final ObjectMapper objectMapper;

    public AssessmentService(AssessmentSessionRepository sessionRepository,
                             VocabItemRepository itemRepository,
                             LearnerRepository learnerRepository,
                             JdbcClient jdbcClient,
                             VocabScoring scoring,
                             ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.learnerRepository = learnerRepository;
        this.jdbcClient = jdbcClient;
        this.scoring = scoring;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionView createSession(UUID learnerId, CreateAssessmentRequest request) {
        learnerRepository.findByIdForUpdate(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习身份"));

        String digest = digest("CREATE_ASSESSMENT");
        Optional<UUID> existingSession = findRequest(learnerId, request.requestId());
        if (existingSession.isPresent()) {
            return view(session(existingSession.get()));
        }

        AssessmentSession open = sessionRepository.findByLearnerIdAndStatus(learnerId, "OPEN").orElse(null);
        if (open != null) {
            saveRequest(learnerId, request.requestId(), digest, open.getId());
            return view(open);
        }

        Form form = publishedForm()
                .orElseThrow(() -> ApiException.notFound("ASSESSMENT_FORM_NOT_FOUND", "测评试卷尚未发布"));
        List<VocabItemRow> items = itemRepository.findByFormId(form.id());
        if (items.size() != form.bandCount() * form.itemsPerBand()) {
            throw new IllegalStateException("测评试卷题量不完整：" + items.size());
        }
        List<VocabItemRow> ordered = interleave(items, form.bandCount());

        Instant now = Instant.now();
        AssessmentSession session = new AssessmentSession(UUID.randomUUID(), learnerId, form.id(),
                ordered.size(), form.algorithmVersion(), now);
        sessionRepository.saveAndFlush(session);

        int position = 1;
        for (VocabItemRow item : ordered) {
            jdbcClient.sql("""
                            insert into assessment_item (session_id, position, item_version_id, options)
                            values (:sessionId, :position, :itemId, :options::jsonb)
                            """)
                    .param("sessionId", session.getId())
                    .param("position", position++)
                    .param("itemId", item.getId())
                    .param("options", item.getOptions())
                    .update();
        }
        saveRequest(learnerId, request.requestId(), digest, session.getId());
        return view(session);
    }

    @Transactional(readOnly = true)
    public SessionView getSession(UUID learnerId, UUID sessionId) {
        return view(owned(sessionId, learnerId));
    }

    @Transactional
    public SessionView submitAnswer(UUID learnerId, UUID sessionId, SubmitAnswerRequest request) {
        AssessmentSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "测评会话不存在"));
        requireOwner(session, learnerId);
        if (!session.isOpen()) {
            throw conflict("SESSION_COMPLETED", "测评已完成");
        }
        if (request.revision() != session.getRevision()) {
            throw conflict("REVISION_CONFLICT", "进度已更新，请重新读取测评");
        }

        ItemRow current = currentItem(session.getId(), session.getPosition())
                .orElseThrow(() -> new IllegalStateException("测评会话缺少当前题项"));

        String digest = digest(request.actionDigest());
        Optional<StoredCommand> previous = command(sessionId, request.requestId());
        if (previous.isPresent()) {
            StoredCommand stored = previous.get();
            if (!stored.digest().equals(digest)) {
                throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同作答内容");
            }
            return objectMapper.readValue(stored.responseBody(), SessionView.class);
        }

        boolean unknown = request.unknown();
        String optionId = request.optionId();
        if (!unknown && (optionId == null || optionId.isBlank())) {
            throw ApiException.badRequest("OPTION_REQUIRED", "请选择释义或标记不认识");
        }
        if (unknown && optionId != null && !optionId.isBlank()) {
            throw ApiException.badRequest("INVALID_UNKNOWN_CHOICE", "标记不认识时不能选择释义");
        }

        boolean correct = false;
        if (!unknown) {
            Option[] options = parseOptions(current.options());
            correct = Arrays.stream(options)
                    .anyMatch(option -> option.id().equals(optionId) && option.correct());
        }

        Instant now = Instant.now();
        jdbcClient.sql("""
                        insert into assessment_answer
                            (session_id, position, item_version_id, option_id, unknown, correct, created_at)
                        values (:sessionId, :position, :itemId, :optionId, :unknown, :correct, :now)
                        """)
                .param("sessionId", sessionId)
                .param("position", current.position())
                .param("itemId", current.itemVersionId())
                .param("optionId", unknown ? null : optionId)
                .param("unknown", unknown)
                .param("correct", correct)
                .param("now", utc(now))
                .update();

        if (session.getPosition() < session.getTotal()) {
            session.advance(now);
        } else {
            session.complete(now);
            saveResult(session, now);
        }

        SessionView response = view(session);
        jdbcClient.sql("""
                        insert into assessment_command
                            (session_id, request_id, request_digest, revision, response_body, created_at)
                        values (:sessionId, :requestId, :digest, :revision, :body, :now)
                        """)
                .param("sessionId", sessionId)
                .param("requestId", request.requestId())
                .param("digest", digest)
                .param("revision", session.getRevision())
                .param("body", objectMapper.writeValueAsString(response))
                .param("now", utc(now))
                .update();
        return response;
    }

    @Transactional(readOnly = true)
    public ResultView result(UUID learnerId, UUID sessionId) {
        AssessmentSession session = owned(sessionId, learnerId);
        if (session.isOpen()) {
            throw conflict("RESULT_NOT_READY", "测评尚未完成");
        }
        return jdbcClient.sql("""
                        select session_id, vocab_band, estimated_families, quality_signal,
                               at_ceiling, unstable, algorithm_version, completed_at, band_stats::text as band_stats
                        from assessment_result where session_id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> new ResultView(
                        UUID.fromString(rs.getString("session_id")),
                        rs.getString("vocab_band"),
                        (Integer) rs.getObject("estimated_families"),
                        rs.getString("quality_signal"),
                        rs.getBoolean("at_ceiling"),
                        rs.getBoolean("unstable"),
                        rs.getString("algorithm_version"),
                        rs.getObject("completed_at", OffsetDateTime.class).toInstant(),
                        Arrays.asList(objectMapper.readValue(rs.getString("band_stats"), BandStatView[].class))))
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("RESULT_NOT_FOUND", "测评结果不存在"));
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(UUID learnerId) {
        return jdbcClient.sql("""
                        select r.session_id, r.vocab_band, r.quality_signal, r.completed_at
                        from assessment_result r
                        where r.learner_id = :learnerId
                        order by r.completed_at desc, r.session_id desc
                        limit 50
                        """)
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> new HistoryView(
                        UUID.fromString(rs.getString("session_id")),
                        rs.getString("vocab_band"),
                        rs.getString("quality_signal"),
                        rs.getObject("completed_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    /**
     * 定制练习使用最近一次完成测评的词汇档；没有测评时由调用方按 V1 处理。
     */
    @Transactional(readOnly = true)
    public Optional<String> latestVocabBand(UUID learnerId) {
        return jdbcClient.sql("""
                        select vocab_band from assessment_result
                        where learner_id = :learnerId
                        order by completed_at desc, session_id desc limit 1
                        """)
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> rs.getString("vocab_band"))
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> latestAssessmentId(UUID learnerId) {
        return jdbcClient.sql("""
                        select session_id from assessment_result
                        where learner_id = :learnerId
                        order by completed_at desc, session_id desc limit 1
                        """)
                .param("learnerId", learnerId)
                .query((rs, rowNum) -> UUID.fromString(rs.getString("session_id")))
                .stream()
                .findFirst();
    }

    private void saveResult(AssessmentSession session, Instant now) {
        int[] correct = new int[7];
        int[] unknown = new int[7];
        jdbcClient.sql("""
                        select a.position, a.correct, a.unknown, v.band
                        from assessment_answer a
                        join assessment_item i on i.session_id = a.session_id and i.position = a.position
                        join vocab_item_version v on v.id = i.item_version_id
                        where a.session_id = :sessionId
                        """)
                .param("sessionId", session.getId())
                .query((rs, rowNum) -> {
                    int band = rs.getInt("band");
                    if (rs.getBoolean("correct")) {
                        correct[band]++;
                    }
                    if (rs.getBoolean("unknown")) {
                        unknown[band]++;
                    }
                    return band;
                })
                .list();
        VocabScoring.Score score = scoring.score(correct, unknown, 6);
        jdbcClient.sql("""
                        insert into assessment_result
                            (session_id, learner_id, vocab_band, estimated_families, quality_signal,
                             at_ceiling, unstable, band_stats, algorithm_version, completed_at)
                        values (:sessionId, :learnerId, :band, :estimated, :signal,
                                :ceiling, :unstable, :stats::jsonb, :algorithm, :now)
                        """)
                .param("sessionId", session.getId())
                .param("learnerId", session.getLearnerId())
                .param("band", score.vocabBand())
                .param("estimated", score.estimatedFamilies())
                .param("signal", score.qualitySignal())
                .param("ceiling", score.atCeiling())
                .param("unstable", score.unstable())
                .param("stats", objectMapper.writeValueAsString(score.bandStats()))
                .param("algorithm", VocabScoring.VERSION)
                .param("now", utc(now))
                .update();
    }

    private SessionView view(AssessmentSession session) {
        int answered = jdbcClient.sql("select count(*) from assessment_answer where session_id = :sessionId")
                .param("sessionId", session.getId())
                .query((rs, rowNum) -> rs.getInt(1))
                .stream()
                .findFirst()
                .orElse(0);
        ItemView item = null;
        if (session.isOpen()) {
            item = currentItem(session.getId(), session.getPosition())
                    .map(row -> new ItemView(row.itemVersionId(), row.position(), row.word(),
                            row.promptZh(), row.contextEn(),
                            Arrays.stream(parseOptions(row.options()))
                                    .map(option -> new OptionView(option.id(), option.label()))
                                    .toList()))
                    .orElse(null);
        }
        return new SessionView(session.getId(), session.getStatus(), session.getPosition(),
                session.getTotal(), session.getRevision(), answered, item);
    }

    private Optional<ItemRow> currentItem(UUID sessionId, int position) {
        return jdbcClient.sql("""
                        select i.position, i.item_version_id as itemVersionId,
                               v.word, v.prompt_zh as promptZh, v.context_en as contextEn,
                               i.options::text as options
                        from assessment_item i
                        join vocab_item_version v on v.id = i.item_version_id
                        where i.session_id = :sessionId and i.position = :position
                        """)
                .param("sessionId", sessionId)
                .param("position", position)
                .query((rs, rowNum) -> new ItemRow(
                        rs.getInt("position"),
                        rs.getString("itemVersionId"),
                        rs.getString("word"),
                        rs.getString("promptZh"),
                        rs.getString("contextEn"),
                        rs.getString("options")))
                .stream()
                .findFirst();
    }

    private Optional<Form> publishedForm() {
        return jdbcClient.sql("""
                        select id, algorithm_version as algorithmVersion,
                               band_count as bandCount, items_per_band as itemsPerBand
                        from vocab_form_version
                        where status = 'PUBLISHED'
                        order by version_no desc limit 1
                        """)
                .query((rs, rowNum) -> new Form(
                        rs.getString("id"),
                        rs.getString("algorithmVersion"),
                        rs.getInt("bandCount"),
                        rs.getInt("itemsPerBand")))
                .stream()
                .findFirst();
    }

    private Optional<UUID> findRequest(UUID learnerId, UUID requestId) {
        return jdbcClient.sql("""
                        select session_id, request_digest from assessment_request
                        where learner_id = :learnerId and request_id = :requestId
                        """)
                .param("learnerId", learnerId)
                .param("requestId", requestId)
                .query((rs, rowNum) -> {
                    if (!digest("CREATE_ASSESSMENT").equals(rs.getString("request_digest"))) {
                        throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同请求内容");
                    }
                    return UUID.fromString(rs.getString("session_id"));
                })
                .stream()
                .findFirst();
    }

    private void saveRequest(UUID learnerId, UUID requestId, String digest, UUID sessionId) {
        jdbcClient.sql("""
                        insert into assessment_request (learner_id, request_id, request_digest, session_id, created_at)
                        values (:learnerId, :requestId, :digest, :sessionId, :now)
                        """)
                .param("learnerId", learnerId)
                .param("requestId", requestId)
                .param("digest", digest)
                .param("sessionId", sessionId)
                .param("now", utc(Instant.now()))
                .update();
    }

    private Optional<StoredCommand> command(UUID sessionId, UUID requestId) {
        return jdbcClient.sql("""
                        select request_digest, response_body from assessment_command
                        where session_id = :sessionId and request_id = :requestId
                        """)
                .param("sessionId", sessionId)
                .param("requestId", requestId)
                .query((rs, rowNum) -> new StoredCommand(
                        rs.getString("request_digest"), rs.getString("response_body")))
                .stream()
                .findFirst();
    }

    private AssessmentSession owned(UUID sessionId, UUID learnerId) {
        AssessmentSession session = session(sessionId);
        requireOwner(session, learnerId);
        return session;
    }

    private AssessmentSession session(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "测评会话不存在"));
    }

    private void requireOwner(AssessmentSession session, UUID learnerId) {
        if (!session.getLearnerId().equals(learnerId)) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "测评会话不存在");
        }
    }

    private List<VocabItemRow> interleave(List<VocabItemRow> items, int bands) {
        Map<Integer, List<VocabItemRow>> byBand = new HashMap<>();
        int max = 0;
        for (VocabItemRow item : items) {
            byBand.computeIfAbsent(item.getBand(), key -> new ArrayList<>()).add(item);
            max = Math.max(max, byBand.get(item.getBand()).size());
        }
        List<VocabItemRow> ordered = new ArrayList<>();
        for (int index = 0; index < max; index++) {
            for (int band = 1; band <= bands; band++) {
                List<VocabItemRow> bandItems = byBand.get(band);
                if (bandItems != null && index < bandItems.size()) {
                    ordered.add(bandItems.get(index));
                }
            }
        }
        return ordered;
    }

    private Option[] parseOptions(String json) {
        return objectMapper.readValue(json, Option[].class);
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

    private record Form(String id, String algorithmVersion, int bandCount, int itemsPerBand) {
    }

    private record ItemRow(int position, String itemVersionId, String word,
                           String promptZh, String contextEn, String options) {
    }

    private record StoredCommand(String digest, String responseBody) {
    }

    private record Option(String id, String label, boolean correct) {
    }
}
