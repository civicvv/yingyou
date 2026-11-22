-- 账号体系：账号资料、一次性令牌、登录会话登记与本地邮件收件箱。
ALTER TABLE practice_session DROP CONSTRAINT practice_session_status_check;
ALTER TABLE practice_session
    ADD CONSTRAINT practice_session_status_check
        CHECK (status IN ('OPEN', 'COMPLETED', 'ABANDONED'));

CREATE TABLE user_account (
    learner_id UUID PRIMARY KEY REFERENCES learner (id),
    email_normalized TEXT NOT NULL,
    email_display TEXT NOT NULL,
    email_verified_at TIMESTAMPTZ,
    password_hash TEXT NOT NULL,
    nickname TEXT NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'LEARNER' CHECK (role IN ('LEARNER', 'ADMIN')),
    terms_version TEXT NOT NULL,
    terms_accepted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_account_email_unique UNIQUE (email_normalized)
);

CREATE TABLE account_token (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner (id),
    purpose VARCHAR(16) NOT NULL
        CHECK (purpose IN ('VERIFY_EMAIL', 'PASSWORD_RESET')),
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT account_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX account_token_learner_idx ON account_token (learner_id, purpose);

CREATE TABLE account_session (
    session_id TEXT PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX account_session_learner_idx ON account_session (learner_id);

CREATE TABLE mail_outbox (
    id UUID PRIMARY KEY,
    recipient TEXT NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX mail_outbox_recipient_idx ON mail_outbox (recipient, created_at DESC);
