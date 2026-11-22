package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PracticeCommandId implements Serializable {

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "request_id")
    private UUID requestId;

    protected PracticeCommandId() {
    }

    public PracticeCommandId(UUID sessionId, UUID requestId) {
        this.sessionId = sessionId;
        this.requestId = requestId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PracticeCommandId that)) {
            return false;
        }
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, requestId);
    }
}
