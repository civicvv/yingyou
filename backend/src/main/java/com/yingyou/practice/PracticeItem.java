package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_item")
public class PracticeItem {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int position;

    @Column(name = "sentence_version_id", nullable = false)
    private String sentenceVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PracticeItemStatus status;

    @Column(name = "hint_used", nullable = false)
    private boolean hintUsed;

    @Column(name = "hint_word_index")
    private Integer hintWordIndex;

    @Column(name = "answer_revealed", nullable = false)
    private boolean answerRevealed;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "first_mismatch_index")
    private Integer firstMismatchIndex;

    @Column(name = "difference_type", length = 24)
    private String differenceType;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    protected PracticeItem() {
    }

    public PracticeItem(UUID id, UUID sessionId, int position, String sentenceVersionId) {
        this.id = id;
        this.sessionId = sessionId;
        this.position = position;
        this.sentenceVersionId = sentenceVersionId;
        this.status = PracticeItemStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getPosition() {
        return position;
    }

    public String getSentenceVersionId() {
        return sentenceVersionId;
    }

    public PracticeItemStatus getStatus() {
        return status;
    }

    public boolean isHintUsed() {
        return hintUsed;
    }

    public Integer getHintWordIndex() {
        return hintWordIndex;
    }

    public boolean isAnswerRevealed() {
        return answerRevealed;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public Integer getFirstMismatchIndex() {
        return firstMismatchIndex;
    }

    public String getDifferenceType() {
        return differenceType;
    }

    public boolean isTerminated() {
        return status != PracticeItemStatus.OPEN;
    }

    public void markWrong(Integer matchedCount, Integer firstMismatchIndex, String differenceType) {
        this.matchedCount = matchedCount;
        this.firstMismatchIndex = firstMismatchIndex;
        this.differenceType = differenceType;
    }

    public void markPassed(boolean assisted, Instant now) {
        this.status = assisted ? PracticeItemStatus.PASSED_ASSISTED : PracticeItemStatus.PASSED_INDEPENDENT;
        this.matchedCount = null;
        this.firstMismatchIndex = null;
        this.differenceType = null;
        this.terminatedAt = now;
    }

    public void markSkipped(Instant now) {
        this.status = PracticeItemStatus.SKIPPED;
        this.terminatedAt = now;
    }

    public void useHint(int wordIndex) {
        if (!this.hintUsed) {
            this.hintUsed = true;
            this.hintWordIndex = wordIndex;
        }
    }

    public void revealAnswer() {
        this.answerRevealed = true;
    }
}
