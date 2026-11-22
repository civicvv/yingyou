package com.yingyou.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class GuestSessionService {

    public static final String LEARNER_ID_ATTRIBUTE = "learnerId";
    private static final int GUEST_SESSION_SECONDS = 7 * 24 * 60 * 60;

    private final LearnerRepository learnerRepository;

    public GuestSessionService(LearnerRepository learnerRepository) {
        this.learnerRepository = learnerRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Learner> currentLearner(HttpSession session) {
        UUID learnerId = readLearnerId(session);
        if (learnerId == null) {
            return Optional.empty();
        }
        Optional<Learner> learner = learnerRepository.findByIdAndStatus(learnerId, LearnerStatus.ACTIVE);
        if (learner.isEmpty()) {
            session.removeAttribute(LEARNER_ID_ATTRIBUTE);
        }
        return learner;
    }

    /**
     * 建立或复用访客身份。会话固定时轮换会话 ID，身份通过 HttpOnly Cookie 维持。
     */
    @Transactional
    public Learner ensureGuest(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        UUID existingId = readLearnerId(session);
        if (existingId != null) {
            Optional<Learner> existing = learnerRepository.findByIdAndStatus(existingId, LearnerStatus.ACTIVE);
            if (existing.isPresent()) {
                session.setMaxInactiveInterval(GUEST_SESSION_SECONDS);
                return existing.get();
            }
            session.removeAttribute(LEARNER_ID_ATTRIBUTE);
        }

        Learner learner = new Learner(UUID.randomUUID(), LearnerKind.GUEST, Instant.now());
        learnerRepository.save(learner);

        request.changeSessionId();
        session.setAttribute(LEARNER_ID_ATTRIBUTE, learner.getId().toString());
        session.setMaxInactiveInterval(GUEST_SESSION_SECONDS);
        return learner;
    }

    private UUID readLearnerId(HttpSession session) {
        Object value = session.getAttribute(LEARNER_ID_ATTRIBUTE);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
