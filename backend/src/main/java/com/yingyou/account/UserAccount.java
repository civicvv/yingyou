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
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "learner_id")
    private UUID learnerId;

    @Column(name = "email_normalized", nullable = false)
    private String emailNormalized;

    @Column(name = "email_display", nullable = false)
    private String emailDisplay;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountRole role;

    @Column(name = "terms_version", nullable = false)
    private String termsVersion;

    @Column(name = "terms_accepted_at", nullable = false)
    private Instant termsAcceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(UUID learnerId, String emailNormalized, String emailDisplay, String passwordHash,
                       String nickname, String termsVersion, Instant now) {
        this.learnerId = learnerId;
        this.emailNormalized = emailNormalized;
        this.emailDisplay = emailDisplay;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = AccountRole.LEARNER;
        this.termsVersion = termsVersion;
        this.termsAcceptedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getLearnerId() {
        return learnerId;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getEmailDisplay() {
        return emailDisplay;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public AccountRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void markEmailVerified(Instant now) {
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = now;
        }
        this.updatedAt = now;
    }

    public void changeNickname(String nickname, Instant now) {
        this.nickname = nickname;
        this.updatedAt = now;
    }

    public void changePasswordHash(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }
}
