-- 词汇测评：固定试卷版本、会话、逐题作答与结果。
CREATE TABLE vocab_form_version (
    id TEXT PRIMARY KEY,
    version_no INT NOT NULL CHECK (version_no > 0),
    algorithm_version TEXT NOT NULL,
    band_count INT NOT NULL CHECK (band_count > 0),
    items_per_band INT NOT NULL CHECK (items_per_band > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vocab_item_version (
    id TEXT PRIMARY KEY,
    form_version_id TEXT NOT NULL REFERENCES vocab_form_version (id),
    band INT NOT NULL CHECK (band BETWEEN 1 AND 6),
    word TEXT NOT NULL,
    context_en TEXT NOT NULL,
    prompt_zh TEXT NOT NULL,
    options JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT vocab_item_version_options_array
        CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) = 4)
);
CREATE INDEX vocab_item_version_form_band_idx ON vocab_item_version (form_version_id, band);

CREATE TABLE assessment_session (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner (id),
    form_version_id TEXT NOT NULL REFERENCES vocab_form_version (id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED')),
    position INT NOT NULL CHECK (position > 0),
    total INT NOT NULL CHECK (total > 0),
    revision BIGINT NOT NULL DEFAULT 0,
    algorithm_version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT assessment_session_completed_fields CHECK (
        (status = 'OPEN' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX assessment_session_open_learner_unique
    ON assessment_session (learner_id) WHERE status = 'OPEN';
CREATE INDEX assessment_session_learner_idx ON assessment_session (learner_id, created_at DESC);

CREATE TABLE assessment_item (
    session_id UUID NOT NULL REFERENCES assessment_session (id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position > 0),
    item_version_id TEXT NOT NULL REFERENCES vocab_item_version (id),
    options JSONB NOT NULL,
    CONSTRAINT assessment_item_pk PRIMARY KEY (session_id, position),
    CONSTRAINT assessment_item_unique_question UNIQUE (session_id, item_version_id)
);

CREATE TABLE assessment_request (
    learner_id UUID NOT NULL REFERENCES learner (id),
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    session_id UUID NOT NULL REFERENCES assessment_session (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT assessment_request_pk PRIMARY KEY (learner_id, request_id)
);

CREATE TABLE assessment_answer (
    session_id UUID NOT NULL REFERENCES assessment_session (id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position > 0),
    item_version_id TEXT NOT NULL REFERENCES vocab_item_version (id),
    option_id TEXT,
    unknown BOOLEAN NOT NULL DEFAULT FALSE,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT assessment_answer_pk PRIMARY KEY (session_id, position),
    CONSTRAINT assessment_answer_choice CHECK (
        (unknown AND option_id IS NULL) OR (NOT unknown AND option_id IS NOT NULL)
    )
);

CREATE TABLE assessment_command (
    session_id UUID NOT NULL REFERENCES assessment_session (id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    revision BIGINT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT assessment_command_pk PRIMARY KEY (session_id, request_id)
);

CREATE TABLE assessment_result (
    session_id UUID PRIMARY KEY REFERENCES assessment_session (id) ON DELETE CASCADE,
    learner_id UUID NOT NULL REFERENCES learner (id),
    vocab_band VARCHAR(2) NOT NULL CHECK (vocab_band IN ('V1', 'V2', 'V3', 'V4')),
    estimated_families INT,
    quality_signal VARCHAR(16) NOT NULL
        CHECK (quality_signal IN ('STANDARD', 'AT_CEILING', 'UNSTABLE')),
    at_ceiling BOOLEAN NOT NULL DEFAULT FALSE,
    unstable BOOLEAN NOT NULL DEFAULT FALSE,
    band_stats JSONB NOT NULL,
    algorithm_version TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX assessment_result_learner_idx ON assessment_result (learner_id, completed_at DESC, session_id DESC);
