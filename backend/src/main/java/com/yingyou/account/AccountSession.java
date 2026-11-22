package com.yingyou.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_session")
public class AccountSession {

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountSession() {
    }

    public AccountSession(String sessionId, UUID learnerId, Instant createdAt) {
        this.sessionId = sessionId;
        this.learnerId = learnerId;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public UUID getLearnerId() {
        return learnerId;
    }
}
