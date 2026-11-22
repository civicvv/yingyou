package com.yingyou.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learner")
public class Learner {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LearnerKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LearnerStatus status;

    @Column(name = "migrated_to_learner_id")
    private UUID migratedToLearnerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Learner() {
    }

    public Learner(UUID id, LearnerKind kind, Instant now) {
        this.id = id;
        this.kind = kind;
        this.status = LearnerStatus.ACTIVE;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public LearnerKind getKind() {
        return kind;
    }

    public LearnerStatus getStatus() {
        return status;
    }
}
