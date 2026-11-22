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
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeScenario scenario;

    @Column(name = "lesson_id")
    private String lessonId;

    @Column(name = "lesson_version_id")
    private String lessonVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeSessionStatus status;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private long revision;

    @Column(name = "grading_version", nullable = false, length = 16)
    private String gradingVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "independent_count")
    private Integer independentCount;

    @Column(name = "assisted_count")
    private Integer assistedCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "first_try_correct_count")
    private Integer firstTryCorrectCount;

    protected PracticeSession() {
    }

    public PracticeSession(UUID id, UUID learnerId, PracticeScenario scenario, String lessonId,
                           String lessonVersionId, int total, String gradingVersion, Instant now) {
        this.id = id;
        this.learnerId = learnerId;
        this.scenario = scenario;
        this.lessonId = lessonId;
        this.lessonVersionId = lessonVersionId;
        this.status = PracticeSessionStatus.OPEN;
        this.position = 1;
        this.total = total;
        this.revision = 0;
        this.gradingVersion = gradingVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLearnerId() {
        return learnerId;
    }

    public PracticeScenario getScenario() {
        return scenario;
    }

    public String getLessonId() {
        return lessonId;
    }

    public String getLessonVersionId() {
        return lessonVersionId;
    }

    public PracticeSessionStatus getStatus() {
        return status;
    }

    public int getPosition() {
        return position;
    }

    public int getTotal() {
        return total;
    }

    public long getRevision() {
        return revision;
    }

    public String getGradingVersion() {
        return gradingVersion;
    }

    public Integer getIndependentCount() {
        return independentCount;
    }

    public Integer getAssistedCount() {
        return assistedCount;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public Integer getFirstTryCorrectCount() {
        return firstTryCorrectCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void moveTo(int nextPosition, Instant now) {
        this.position = nextPosition;
        this.revision++;
        this.updatedAt = now;
    }

    public void complete(int independent, int assisted, int skipped, int firstTryCorrect, Instant now) {
        this.status = PracticeSessionStatus.COMPLETED;
        this.completedAt = now;
        this.independentCount = independent;
        this.assistedCount = assisted;
        this.skippedCount = skipped;
        this.firstTryCorrectCount = firstTryCorrect;
        this.revision++;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.revision++;
        this.updatedAt = now;
    }
}
