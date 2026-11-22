package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_session_request")
public class PracticeSessionRequest {

    @EmbeddedId
    private PracticeSessionRequestId id;

    @Column(name = "request_digest", nullable = false)
    private String requestDigest;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PracticeSessionRequest() {
    }

    public PracticeSessionRequest(PracticeSessionRequestId id, String requestDigest, UUID sessionId,
                                  Instant createdAt) {
        this.id = id;
        this.requestDigest = requestDigest;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    public PracticeSessionRequestId getId() {
        return id;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
