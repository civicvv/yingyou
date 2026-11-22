package com.yingyou.practice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "practice_command")
public class PracticeCommand {

    @EmbeddedId
    private PracticeCommandId id;

    @Column(name = "request_digest", nullable = false)
    private String requestDigest;

    @Column(nullable = false)
    private long revision;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PracticeCommand() {
    }

    public PracticeCommand(PracticeCommandId id, String requestDigest, long revision, String responseBody,
                           Instant createdAt) {
        this.id = id;
        this.requestDigest = requestDigest;
        this.revision = revision;
        this.responseBody = responseBody;
        this.createdAt = createdAt;
    }

    public PracticeCommandId getId() {
        return id;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
