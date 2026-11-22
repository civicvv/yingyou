-- 课节级学习进度：每个学习者与每个稳定课节一行，与会话终结同事务更新。
CREATE TABLE learner_lesson_progress (
    learner_id UUID NOT NULL REFERENCES learner (id),
    lesson_id TEXT NOT NULL REFERENCES lesson (id),
    last_active_session_id UUID REFERENCES practice_session (id),
    last_completed_session_id UUID REFERENCES practice_session (id),
    first_completed_at TIMESTAMPTZ,
    last_practiced_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT learner_lesson_progress_pk PRIMARY KEY (learner_id, lesson_id),
    CONSTRAINT learner_lesson_progress_completed_fields CHECK (
        (last_completed_session_id IS NULL AND first_completed_at IS NULL)
        OR (last_completed_session_id IS NOT NULL AND first_completed_at IS NOT NULL)
    )
);

CREATE INDEX learner_lesson_progress_learner_recent_idx
    ON learner_lesson_progress (learner_id, last_practiced_at DESC);
