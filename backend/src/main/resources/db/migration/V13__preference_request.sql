CREATE TABLE preference_request (
    learner_id UUID NOT NULL REFERENCES learner (id),
    request_id UUID NOT NULL,
    request_digest TEXT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT preference_request_pk PRIMARY KEY (learner_id, request_id)
);
