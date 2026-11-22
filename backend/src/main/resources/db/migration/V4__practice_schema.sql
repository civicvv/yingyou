-- 练习会话、题项、作答事实与命令幂等记录。
CREATE TABLE practice_session (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner (id),
    scenario VARCHAR(16) NOT NULL CHECK (scenario IN ('LESSON', 'PERSONALIZED', 'REVIEW')),
    lesson_id TEXT REFERENCES lesson (id),
    lesson_version_id TEXT REFERENCES lesson_version (id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'COMPLETED')),
    position INT NOT NULL CHECK (position > 0),
    total INT NOT NULL CHECK (total > 0),
    revision BIGINT NOT NULL DEFAULT 0,
    grading_version VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    independent_count INT,
    assisted_count INT,
    skipped_count INT,
    first_try_correct_count INT,
    CONSTRAINT practice_session_lesson_refs CHECK (
        (scenario = 'LESSON' AND lesson_id IS NOT NULL AND lesson_version_id IS NOT NULL)
        OR (scenario <> 'LESSON' AND lesson_id IS NULL AND lesson_version_id IS NULL)
    ),
    CONSTRAINT practice_session_completed_fields CHECK (
        (status = 'OPEN' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX practice_session_open_lesson_unique
    ON practice_session (learner_id, lesson_id)
    WHERE status = 'OPEN' AND scenario = 'LESSON';

CREATE INDEX practice_session_learner_lesson_idx ON practice_session (learner_id, lesson_id);

CREATE TABLE practice_item (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES practice_session (id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position > 0),
    sentence_version_id TEXT NOT NULL REFERENCES sentence_version (id),
    status VARCHAR(24) NOT NULL
        CHECK (status IN ('OPEN', 'PASSED_INDEPENDENT', 'PASSED_ASSISTED', 'SKIPPED')),
    hint_used BOOLEAN NOT NULL DEFAULT FALSE,
    answer_revealed BOOLEAN NOT NULL DEFAULT FALSE,
    matched_count INT,
    first_mismatch_index INT,
    difference_type VARCHAR(24),
    terminated_at TIMESTAMPTZ,
    CONSTRAINT practice_item_position_unique UNIQUE (session_id, position),
    CONSTRAINT practice_item_terminated_fields CHECK (
        (status = 'OPEN' AND terminated_at IS NULL)
        OR (status <> 'OPEN' AND terminated_at IS NOT NULL)
    )
);

CREATE TABLE attempt (
    id UUID PRIMARY KEY,
    practice_item_id UUID NOT NULL REFERENCES practice_item (id) ON DELETE CASCADE,
    ordinal INT NOT NULL CHECK (ordinal > 0),
    raw_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    assisted BOOLEAN NOT NULL,
    matched_count INT,
    first_mismatch_index INT,
    difference_type VARCHAR(24),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT attempt_ordinal_unique UNIQUE (practice_item_id, ordinal)
);

CREATE TABLE practice_command (
    session_id UUID NOT NULL REFERENCES practice_session (id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    revision BIGINT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT practice_command_pk PRIMARY KEY (session_id, request_id)
);

CREATE TABLE practice_session_request (
    learner_id UUID NOT NULL REFERENCES learner (id),
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    session_id UUID NOT NULL REFERENCES practice_session (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT practice_session_request_pk PRIMARY KEY (learner_id, request_id)
);
