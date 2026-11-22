-- 定制练习：目标偏好、分档状态、批次选题快照与幂等请求。
ALTER TABLE practice_session ADD COLUMN exam_track VARCHAR(20);

ALTER TABLE practice_session ADD CONSTRAINT practice_session_exam_track_check CHECK (
    (scenario = 'PERSONALIZED' AND exam_track IN ('CET4', 'CET6', 'IELTS_ACADEMIC', 'TOEFL_IBT', 'GENERAL'))
    OR (scenario <> 'PERSONALIZED' AND exam_track IS NULL)
);

CREATE UNIQUE INDEX practice_session_open_personalized_unique
    ON practice_session (learner_id, exam_track)
    WHERE status = 'OPEN' AND scenario = 'PERSONALIZED';

CREATE TABLE learner_exam_preference (
    learner_id UUID PRIMARY KEY REFERENCES learner (id),
    exam_track VARCHAR(20) NOT NULL
        CHECK (exam_track IN ('CET4', 'CET6', 'IELTS_ACADEMIC', 'TOEFL_IBT', 'GENERAL')),
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE learner_track_placement (
    learner_id UUID NOT NULL REFERENCES learner (id),
    exam_track VARCHAR(20) NOT NULL
        CHECK (exam_track IN ('CET4', 'CET6', 'IELTS_ACADEMIC', 'TOEFL_IBT', 'GENERAL')),
    auto_difficulty VARCHAR(2) NOT NULL CHECK (auto_difficulty IN ('D1', 'D2', 'D3', 'D4')),
    manual_difficulty VARCHAR(2) CHECK (manual_difficulty IN ('D1', 'D2', 'D3', 'D4')),
    rule_version TEXT NOT NULL,
    last_evaluated_session_id UUID REFERENCES practice_session (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT learner_track_placement_pk PRIMARY KEY (learner_id, exam_track)
);

CREATE TABLE practice_selection (
    practice_session_id UUID PRIMARY KEY REFERENCES practice_session (id) ON DELETE CASCADE,
    exam_track VARCHAR(20) NOT NULL
        CHECK (exam_track IN ('CET4', 'CET6', 'IELTS_ACADEMIC', 'TOEFL_IBT', 'GENERAL')),
    assessment_id UUID REFERENCES assessment_session (id),
    vocab_band VARCHAR(2) NOT NULL CHECK (vocab_band IN ('V1', 'V2', 'V3', 'V4')),
    baseline_difficulty VARCHAR(2) NOT NULL CHECK (baseline_difficulty IN ('D1', 'D2', 'D3', 'D4')),
    rule_version TEXT NOT NULL,
    fallback_reason TEXT,
    selected_sentence_versions JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT practice_selection_versions_array CHECK (
        jsonb_typeof(selected_sentence_versions) = 'array'
        AND jsonb_array_length(selected_sentence_versions) > 0
    )
);

CREATE TABLE personalized_request (
    learner_id UUID NOT NULL REFERENCES learner (id),
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    session_id UUID NOT NULL REFERENCES practice_session (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT personalized_request_pk PRIMARY KEY (learner_id, request_id)
);
