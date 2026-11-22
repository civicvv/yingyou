package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class PracticeSessionRequestId implements Serializable {

    @Column(name = "learner_id")
    private UUID learnerId;

    @Column(name = "request_id")
    private UUID requestId;

    protected PracticeSessionRequestId() {
    }

    public PracticeSessionRequestId(UUID learnerId, UUID requestId) {
        this.learnerId = learnerId;
        this.requestId = requestId;
    }

    public UUID getLearnerId() {
        return learnerId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PracticeSessionRequestId that)) {
            return false;
        }
        return Objects.equals(learnerId, that.learnerId) && Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(learnerId, requestId);
    }
}
