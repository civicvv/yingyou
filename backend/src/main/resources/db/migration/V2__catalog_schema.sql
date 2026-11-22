-- 学习身份：访客与账号共用同一业务身份模型。
CREATE TABLE learner (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('GUEST', 'ACCOUNT')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'MIGRATED', 'DELETED')),
    migrated_to_learner_id UUID REFERENCES learner (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 课程与不可变版本题库。
CREATE TABLE course (
    id TEXT PRIMARY KEY,
    exam_track VARCHAR(20) NOT NULL
        CHECK (exam_track IN ('CET4', 'CET6', 'IELTS_ACADEMIC', 'TOEFL_IBT', 'GENERAL')),
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lesson (
    id TEXT PRIMARY KEY,
    course_id TEXT NOT NULL REFERENCES course (id),
    title TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    published_version_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX lesson_course_sort_idx ON lesson (course_id, sort_order);

CREATE TABLE lesson_version (
    id TEXT PRIMARY KEY,
    lesson_id TEXT NOT NULL REFERENCES lesson (id),
    version_no INT NOT NULL CHECK (version_no > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT lesson_version_no_unique UNIQUE (lesson_id, version_no)
);

ALTER TABLE lesson
    ADD CONSTRAINT lesson_published_version_fk
        FOREIGN KEY (published_version_id) REFERENCES lesson_version (id);

CREATE TABLE sentence (
    id TEXT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sentence_version (
    id TEXT PRIMARY KEY,
    sentence_id TEXT NOT NULL REFERENCES sentence (id),
    version_no INT NOT NULL CHECK (version_no > 0),
    prompt_zh TEXT NOT NULL,
    context_zh TEXT,
    canonical_en TEXT NOT NULL,
    answer_variants JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation_segments JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation_zh TEXT,
    grammar_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    exam_tracks JSONB NOT NULL,
    difficulty VARCHAR(2) NOT NULL CHECK (difficulty IN ('D1', 'D2', 'D3', 'D4')),
    topic_code TEXT NOT NULL,
    primary_skill TEXT NOT NULL,
    skill_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_lexemes JSONB NOT NULL DEFAULT '[]'::jsonb,
    min_vocab_band VARCHAR(2) NOT NULL CHECK (min_vocab_band IN ('V1', 'V2', 'V3', 'V4')),
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('ORIGINAL', 'LICENSED')),
    source_ref TEXT,
    license_status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sentence_version_no_unique UNIQUE (sentence_id, version_no),
    CONSTRAINT sentence_version_answer_variants_array
        CHECK (jsonb_typeof(answer_variants) = 'array'),
    CONSTRAINT sentence_version_explanation_segments_array
        CHECK (jsonb_typeof(explanation_segments) = 'array'),
    CONSTRAINT sentence_version_grammar_tags_array
        CHECK (jsonb_typeof(grammar_tags) = 'array'),
    CONSTRAINT sentence_version_exam_tracks_array
        CHECK (jsonb_typeof(exam_tracks) = 'array' AND jsonb_array_length(exam_tracks) > 0),
    CONSTRAINT sentence_version_skill_codes_array
        CHECK (jsonb_typeof(skill_codes) = 'array'),
    CONSTRAINT sentence_version_target_lexemes_array
        CHECK (jsonb_typeof(target_lexemes) = 'array')
);

CREATE INDEX sentence_version_sentence_idx ON sentence_version (sentence_id);

CREATE TABLE lesson_version_item (
    lesson_version_id TEXT NOT NULL REFERENCES lesson_version (id),
    position INT NOT NULL CHECK (position > 0),
    sentence_version_id TEXT NOT NULL REFERENCES sentence_version (id),
    CONSTRAINT lesson_version_item_pk PRIMARY KEY (lesson_version_id, position),
    CONSTRAINT lesson_version_item_sentence_unique UNIQUE (lesson_version_id, sentence_version_id)
);

CREATE INDEX lesson_version_item_sentence_idx ON lesson_version_item (sentence_version_id);
