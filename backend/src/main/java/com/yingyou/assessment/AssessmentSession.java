package com.yingyou.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessment_session")
public class AssessmentSession {

    @Id
    private UUID id;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "form_version_id", nullable = false)
    private String formVersionId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private long revision;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AssessmentSession() {
    }

    public AssessmentSession(UUID id, UUID learnerId, String formVersionId, int total,
                             String algorithmVersion, Instant now) {
        this.id = id;
        this.learnerId = learnerId;
        this.formVersionId = formVersionId;
        this.status = "OPEN";
        this.position = 1;
        this.total = total;
        this.revision = 0;
        this.algorithmVersion = algorithmVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLearnerId() {
        return learnerId;
    }

    public String getFormVersionId() {
        return formVersionId;
    }

    public String getStatus() {
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

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public void advance(Instant now) {
        if (position >= total) {
            throw new IllegalStateException("测评已到最后一题");
        }
        position++;
        revision++;
        updatedAt = now;
    }

    public void complete(Instant now) {
        this.status = "COMPLETED";
        this.completedAt = now;
        this.revision++;
        this.updatedAt = now;
    }
}
