package com.yingyou.practice;

import com.yingyou.account.LearnerRepository;
import com.yingyou.catalog.Course;
import com.yingyou.catalog.CourseRepository;
import com.yingyou.catalog.CourseStatus;
import com.yingyou.catalog.Lesson;
import com.yingyou.catalog.LessonRepository;
import com.yingyou.catalog.LessonVersionItem;
import com.yingyou.catalog.LessonVersionItemRepository;
import com.yingyou.progress.ProgressService;
import com.yingyou.shared.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PracticeService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeItemRepository itemRepository;
    private final PracticeAttemptRepository attemptRepository;
    private final PracticeCommandRepository commandRepository;
    private final PracticeSessionRequestRepository sessionRequestRepository;
    private final LearnerRepository learnerRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final LessonVersionItemRepository lessonVersionItemRepository;
    private final SentenceContentRepository contentRepository;
    private final GradingService gradingService;
    private final PrefixCheckThrottle prefixCheckThrottle;
    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    public PracticeService(PracticeSessionRepository sessionRepository,
                           PracticeItemRepository itemRepository,
                           PracticeAttemptRepository attemptRepository,
                           PracticeCommandRepository commandRepository,
                           PracticeSessionRequestRepository sessionRequestRepository,
                           LearnerRepository learnerRepository,
                           LessonRepository lessonRepository,
                           CourseRepository courseRepository,
                           LessonVersionItemRepository lessonVersionItemRepository,
                           SentenceContentRepository contentRepository,
                           GradingService gradingService,
                           PrefixCheckThrottle prefixCheckThrottle,
                           ProgressService progressService,
                           ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
        this.commandRepository = commandRepository;
        this.sessionRequestRepository = sessionRequestRepository;
        this.learnerRepository = learnerRepository;
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.lessonVersionItemRepository = lessonVersionItemRepository;
        this.contentRepository = contentRepository;
        this.gradingService = gradingService;
        this.prefixCheckThrottle = prefixCheckThrottle;
        this.progressService = progressService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticeSessionView createSession(UUID learnerId, CreatePracticeSessionRequest request) {
        learnerRepository.findByIdForUpdate(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习者会话"));

        String digest = digest("CREATE|" + request.lessonId());
        PracticeSessionRequestId requestId = new PracticeSessionRequestId(learnerId, request.requestId());
        Optional<PracticeSessionRequest> existingRequest = sessionRequestRepository.findById(requestId);
        if (existingRequest.isPresent()) {
            PracticeSessionRequest record = existingRequest.get();
            if (!record.getRequestDigest().equals(digest)) {
                throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同请求内容");
            }
            PracticeSession session = sessionRepository.findById(record.getSessionId())
                    .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "练习会话不存在"));
            return view(session);
        }

        Lesson lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "课节不存在"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "课节不存在"));
        if (course.getStatus() != CourseStatus.PUBLISHED || lesson.getPublishedVersionId() == null) {
            throw ApiException.notFound("LESSON_NOT_FOUND", "课节不存在或尚未发布");
        }

        PracticeSession session = sessionRepository.findOpenLessonSession(learnerId, lesson.getId())
                .orElseGet(() -> createNewSession(learnerId, lesson));

        // 先落库会话，再写入引用其主键的课节进度。
        sessionRepository.flush();
        progressService.touchLesson(learnerId, lesson.getId(), session.getId());
        sessionRequestRepository.save(new PracticeSessionRequest(requestId, digest, session.getId(), Instant.now()));
        return view(session);
    }

    @Transactional(readOnly = true)
    public PracticeSessionView getSession(UUID learnerId, UUID sessionId) {
        PracticeSession session = requireOwnedSession(learnerId, sessionId);
        if (session.getStatus() == PracticeSessionStatus.ABANDONED) {
            throw conflict("SESSION_ABANDONED", "练习会话已结束");
        }
        return view(session);
    }

    @Transactional(readOnly = true)
    public PrefixCheckResponse prefixCheck(UUID learnerId, UUID sessionId, PrefixCheckRequest request) {
        PracticeSession session = requireOwnedSession(learnerId, sessionId);
        if (session.getStatus() != PracticeSessionStatus.OPEN) {
            throw session.getStatus() == PracticeSessionStatus.ABANDONED
                    ? conflict("SESSION_ABANDONED", "练习会话已结束")
                    : conflict("SESSION_COMPLETED", "练习会话已完成");
        }
        if (request.revision() != session.getRevision()) {
            throw conflict("REVISION_CONFLICT", "进度已更新，请重新读取会话");
        }
        PracticeItem item = currentItem(session);
        if (!item.getId().equals(request.itemId())) {
            throw conflict("ITEM_MISMATCH", "当前题目已变化，请重新读取会话");
        }
        if (item.isTerminated()) {
            throw conflict("ITEM_TERMINATED", "本题已结束");
        }
        validateInput(request.text());
        if (!prefixCheckThrottle.allow(sessionId)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "预检请求过于频繁，请稍后再试");
        }

        SentenceContentRepository.SentenceContent content = content(item.getSentenceVersionId());
        GradingService.PrefixResult prefix = gradingService.checkPrefix(
                request.text(), content.canonicalEn(), content.answerVariants());
        return new PrefixCheckResponse(session.getRevision(), item.getId(),
                prefix.matchedCount(), prefix.firstMismatchIndex());
    }

    @Transactional
    public PracticeCommandResponse command(UUID learnerId, UUID sessionId, PracticeCommandRequest request) {
        PracticeSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "练习会话不存在"));
        requireOwner(session, learnerId);

        String digest = commandDigest(request);
        PracticeCommandId commandId = new PracticeCommandId(sessionId, request.requestId());
        Optional<PracticeCommand> existingCommand = commandRepository.findById(commandId);
        if (existingCommand.isPresent()) {
            PracticeCommand command = existingCommand.get();
            if (!command.getRequestDigest().equals(digest)) {
                throw conflict("REQUEST_ID_CONFLICT", "相同请求号对应不同请求内容");
            }
            return readResponse(command.getResponseBody());
        }

        if (session.getStatus() != PracticeSessionStatus.OPEN) {
            throw session.getStatus() == PracticeSessionStatus.ABANDONED
                    ? conflict("SESSION_ABANDONED", "练习会话已结束")
                    : conflict("SESSION_COMPLETED", "练习会话已完成");
        }
        if (request.revision() != session.getRevision()) {
            throw conflict("REVISION_CONFLICT", "进度已更新，请重新读取会话");
        }

        PracticeItem item = currentItem(session);
        if (!item.getId().equals(request.itemId())) {
            throw conflict("ITEM_MISMATCH", "当前题目已变化，请重新读取会话");
        }

        CommandAction action = parseAction(request.action());
        Instant now = Instant.now();
        UUID attemptId = null;

        switch (action) {
            case SUBMIT -> {
                attemptId = submit(item, request.text(), now);
                session.touch(now);
            }
            case HINT -> {
                useHint(item, request.wordIndex());
                session.touch(now);
            }
            case REVEAL -> {
                reveal(item);
                session.touch(now);
            }
            case SKIP -> {
                skip(item, now);
                session.touch(now);
            }
            case NEXT -> advance(session, item, now);
        }

        PracticeCommandResponse response = new PracticeCommandResponse(view(session), attemptId);
        if (session.getLessonId() != null) {
            if (session.getStatus() == PracticeSessionStatus.COMPLETED) {
                progressService.completeLesson(learnerId, session.getLessonId(), session.getId());
            } else {
                progressService.touchLesson(learnerId, session.getLessonId(), session.getId());
            }
        }
        commandRepository.save(new PracticeCommand(commandId, digest, session.getRevision(),
                writeResponse(response), now));
        return response;
    }

    @Transactional(readOnly = true)
    public PracticeResultView getResult(UUID learnerId, UUID sessionId) {
        PracticeSession session = requireOwnedSession(learnerId, sessionId);
        if (session.getStatus() != PracticeSessionStatus.COMPLETED) {
            throw conflict("RESULT_NOT_READY", "练习尚未完成");
        }

        List<PracticeItem> items = itemRepository.findBySessionIdOrderByPositionAsc(sessionId);
        List<PracticeResultItemView> itemViews = new ArrayList<>();
        for (PracticeItem item : items) {
            SentenceContentRepository.SentenceContent content = content(item.getSentenceVersionId());
            List<PracticeAttempt> attempts = attemptRepository.findByPracticeItemIdOrderByOrdinalAsc(item.getId());
            boolean firstTryCorrect = !attempts.isEmpty()
                    && attempts.get(0).isCorrect()
                    && !attempts.get(0).isAssisted();
            itemViews.add(new PracticeResultItemView(
                    item.getPosition(),
                    content.promptZh(),
                    content.canonicalEn(),
                    item.getStatus().name(),
                    item.isHintUsed(),
                    item.isAnswerRevealed(),
                    attempts.size(),
                    firstTryCorrect));
        }

        int total = session.getTotal();
        int firstTryCorrect = session.getFirstTryCorrectCount() == null ? 0 : session.getFirstTryCorrectCount();
        double accuracy = total == 0 ? 0 : Math.round(firstTryCorrect * 10000.0 / total) / 10000.0;

        Lesson lesson = lesson(session);
        return new PracticeResultView(
                session.getId(),
                lesson == null ? null : lesson.getCourseId(),
                session.getLessonId(),
                session.getLessonVersionId(),
                lesson == null ? null : lesson.getTitle(),
                total,
                session.getIndependentCount() == null ? 0 : session.getIndependentCount(),
                session.getAssistedCount() == null ? 0 : session.getAssistedCount(),
                session.getSkippedCount() == null ? 0 : session.getSkippedCount(),
                firstTryCorrect,
                accuracy,
                itemViews);
    }

    private PracticeSession createNewSession(UUID learnerId, Lesson lesson) {
        List<LessonVersionItem> items = lessonVersionItemRepository
                .findByLessonVersionIdOrderByPosition(lesson.getPublishedVersionId());
        if (items.isEmpty()) {
            throw conflict("LESSON_EMPTY", "课节暂无题目");
        }

        Instant now = Instant.now();
        PracticeSession session = new PracticeSession(UUID.randomUUID(), learnerId, PracticeScenario.LESSON,
                lesson.getId(), lesson.getPublishedVersionId(), items.size(), GradingService.VERSION, now);
        sessionRepository.save(session);

        int position = 1;
        for (LessonVersionItem item : items) {
            itemRepository.save(new PracticeItem(UUID.randomUUID(), session.getId(), position++,
                    item.getSentenceVersionId()));
        }
        return session;
    }

    private UUID submit(PracticeItem item, String text, Instant now) {
        if (item.isTerminated()) {
            throw conflict("ITEM_TERMINATED", "本题已结束");
        }
        validateInput(text);
        SentenceContentRepository.SentenceContent content = content(item.getSentenceVersionId());
        GradingService.GradeResult grade = gradingService.grade(
                text, content.canonicalEn(), content.answerVariants());

        boolean assisted = item.isHintUsed() || item.isAnswerRevealed();
        int ordinal = (int) attemptRepository.countByPracticeItemId(item.getId()) + 1;
        PracticeAttempt attempt = new PracticeAttempt(UUID.randomUUID(), item.getId(), ordinal, text,
                grade.normalizedText(), grade.correct(), assisted, grade.matchedCount(),
                grade.firstMismatchIndex(), grade.differenceType(), now);
        attemptRepository.save(attempt);

        if (grade.correct()) {
            item.markPassed(assisted, now);
        } else {
            item.markWrong(grade.matchedCount(), grade.firstMismatchIndex(), grade.differenceType());
        }
        return attempt.getId();
    }

    private void useHint(PracticeItem item, Integer wordIndex) {
        if (item.isTerminated()) {
            throw conflict("ITEM_TERMINATED", "本题已结束");
        }
        if (item.isHintUsed()) {
            return;
        }
        List<String> words = gradingService.displayWords(content(item.getSentenceVersionId()).canonicalEn());
        int index = wordIndex == null ? 0 : wordIndex;
        if (index < 0 || index >= words.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_WORD_INDEX",
                    "提示词位置超出题目范围");
        }
        item.useHint(index);
    }

    private void reveal(PracticeItem item) {
        if (item.isTerminated()) {
            throw conflict("ITEM_TERMINATED", "本题已结束");
        }
        if (!item.isAnswerRevealed()) {
            item.revealAnswer();
        }
    }

    private void skip(PracticeItem item, Instant now) {
        if (item.isTerminated()) {
            throw conflict("ITEM_TERMINATED", "本题已结束");
        }
        item.markSkipped(now);
    }

    private void advance(PracticeSession session, PracticeItem item, Instant now) {
        if (!item.isTerminated()) {
            throw conflict("ITEM_NOT_TERMINATED", "请先完成或跳过本题");
        }
        if (session.getPosition() < session.getTotal()) {
            session.moveTo(session.getPosition() + 1, now);
        } else {
            completeSession(session, now);
        }
    }

    private void completeSession(PracticeSession session, Instant now) {
        List<PracticeItem> items = itemRepository.findBySessionIdOrderByPositionAsc(session.getId());
        int independent = 0;
        int assisted = 0;
        int skipped = 0;
        int firstTryCorrect = 0;

        for (PracticeItem item : items) {
            switch (item.getStatus()) {
                case PASSED_INDEPENDENT -> independent++;
                case PASSED_ASSISTED -> assisted++;
                case SKIPPED -> skipped++;
                case OPEN -> {
                }
            }
            List<PracticeAttempt> attempts = attemptRepository.findByPracticeItemIdOrderByOrdinalAsc(item.getId());
            if (!attempts.isEmpty() && attempts.get(0).isCorrect() && !attempts.get(0).isAssisted()) {
                firstTryCorrect++;
            }
        }
        session.complete(independent, assisted, skipped, firstTryCorrect, now);
    }

    private void validateInput(String text) {
        if (text == null || text.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_INPUT", "请输入英文句子");
        }
        if (gradingService.isTooLong(text)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INPUT_TOO_LONG",
                    "输入过长，最多 500 个字符");
        }
        if (gradingService.normalize(text).isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_INPUT", "请输入英文句子");
        }
    }

    private PracticeSessionView view(PracticeSession session) {
        Lesson lesson = lesson(session);
        PracticeItem item = session.getStatus() == PracticeSessionStatus.OPEN ? currentItem(session) : null;

        PracticeItemView itemView = null;
        PracticeFeedbackView feedback = null;
        if (item != null) {
            SentenceContentRepository.SentenceContent content = content(item.getSentenceVersionId());
            List<String> words = gradingService.displayWords(content.canonicalEn());
            List<Integer> wordLengths = words.stream()
                    .map(word -> word.codePointCount(0, word.length()))
                    .toList();
            itemView = new PracticeItemView(item.getId(), content.promptZh(), content.contextZh(),
                    item.getStatus().name(), item.isHintUsed(), item.isAnswerRevealed(), wordLengths);
            feedback = feedback(session, item, content);
        }

        return new PracticeSessionView(
                session.getId(),
                session.getStatus().name(),
                session.getRevision(),
                session.getPosition(),
                session.getTotal(),
                lesson == null ? null : lesson.getCourseId(),
                session.getLessonId(),
                lesson == null ? null : lesson.getTitle(),
                itemView,
                feedback);
    }

    private PracticeFeedbackView feedback(PracticeSession session, PracticeItem item,
                                          SentenceContentRepository.SentenceContent content) {
        boolean reveal = item.isAnswerRevealed() || item.isTerminated();
        String answer = reveal ? content.canonicalEn() : null;
        String explanation = reveal ? content.explanationZh() : null;
        List<ExplanationSegmentView> segments = reveal ? content.explanationSegments() : null;
        List<String> grammarTags = reveal ? content.grammarTags() : null;
        String hint = item.isHintUsed() && !reveal ? hintWord(item, content) : null;

        NextPreviewView nextPreview = null;
        if (item.isTerminated() && session.getStatus() == PracticeSessionStatus.OPEN
                && session.getPosition() < session.getTotal()) {
            PracticeItem nextItem = itemRepository
                    .findBySessionIdAndPosition(session.getId(), session.getPosition() + 1)
                    .orElse(null);
            if (nextItem != null) {
                SentenceContentRepository.SentenceContent nextContent =
                        content(nextItem.getSentenceVersionId());
                nextPreview = new NextPreviewView(nextItem.getPosition(), nextContent.promptZh());
            }
        }

        boolean hasFeedback = answer != null || hint != null || item.getMatchedCount() != null;
        if (!hasFeedback) {
            return null;
        }
        return new PracticeFeedbackView(answer, explanation, segments, grammarTags,
                item.getMatchedCount(), item.getFirstMismatchIndex(), item.getDifferenceType(),
                hint, nextPreview);
    }

    private PracticeSession requireOwnedSession(UUID learnerId, UUID sessionId) {
        PracticeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "练习会话不存在"));
        requireOwner(session, learnerId);
        return session;
    }

    private void requireOwner(PracticeSession session, UUID learnerId) {
        if (!session.getLearnerId().equals(learnerId)) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "练习会话不存在");
        }
    }

    private PracticeItem currentItem(PracticeSession session) {
        return itemRepository.findBySessionIdAndPosition(session.getId(), session.getPosition())
                .orElseThrow(() -> new IllegalStateException("练习会话缺少当前题项"));
    }

    private Lesson lesson(PracticeSession session) {
        if (session.getLessonId() == null) {
            return null;
        }
        return lessonRepository.findById(session.getLessonId()).orElse(null);
    }

    private SentenceContentRepository.SentenceContent content(String sentenceVersionId) {
        return contentRepository.findById(sentenceVersionId)
                .orElseThrow(() -> new IllegalStateException("题目版本不存在：" + sentenceVersionId));
    }

    private String hintWord(PracticeItem item, SentenceContentRepository.SentenceContent content) {
        List<String> words = gradingService.displayWords(content.canonicalEn());
        int index = item.getHintWordIndex() == null ? 0 : item.getHintWordIndex();
        return index >= 0 && index < words.size() ? words.get(index) : null;
    }

    private CommandAction parseAction(String action) {
        try {
            return CommandAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_ACTION", "不支持的练习操作");
        }
    }

    private String commandDigest(PracticeCommandRequest request) {
        return digest(request.action() + "|" + request.itemId() + "|"
                + (request.text() == null ? "" : request.text()) + "|"
                + (request.wordIndex() == null ? "" : request.wordIndex()));
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String writeResponse(PracticeCommandResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    private PracticeCommandResponse readResponse(String body) {
        return objectMapper.readValue(body, PracticeCommandResponse.class);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private enum CommandAction {
        SUBMIT,
        HINT,
        REVEAL,
        SKIP,
        NEXT
    }
}
