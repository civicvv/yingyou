package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attempt")
public class PracticeAttempt {

    @Id
    private UUID id;

    @Column(name = "practice_item_id", nullable = false)
    private UUID practiceItemId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "normalized_text", nullable = false)
    private String normalizedText;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private boolean assisted;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "first_mismatch_index")
    private Integer firstMismatchIndex;

    @Column(name = "difference_type", length = 24)
    private String differenceType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PracticeAttempt() {
    }

    public PracticeAttempt(UUID id, UUID practiceItemId, int ordinal, String rawText, String normalizedText,
                           boolean correct, boolean assisted, Integer matchedCount, Integer firstMismatchIndex,
                           String differenceType, Instant now) {
        this.id = id;
        this.practiceItemId = practiceItemId;
        this.ordinal = ordinal;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.correct = correct;
        this.assisted = assisted;
        this.matchedCount = matchedCount;
        this.firstMismatchIndex = firstMismatchIndex;
        this.differenceType = differenceType;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPracticeItemId() {
        return practiceItemId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public boolean isCorrect() {
        return correct;
    }

    public boolean isAssisted() {
        return assisted;
    }
}
