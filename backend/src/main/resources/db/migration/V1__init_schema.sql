CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE subjects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE topics (
    id          BIGSERIAL PRIMARY KEY,
    subject_id  BIGINT       NOT NULL REFERENCES subjects (id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    order_index INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_topics_subject_name UNIQUE (subject_id, name)
);
CREATE INDEX idx_topics_subject ON topics (subject_id);

CREATE TABLE questions (
    id          BIGSERIAL PRIMARY KEY,
    topic_id    BIGINT      NOT NULL REFERENCES topics (id),
    text        TEXT        NOT NULL,
    explanation TEXT,
    difficulty  VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_questions_topic ON questions (topic_id);

-- Variantlar ham soft delete qilinadi: eski sessiyalardagi tanlangan javoblar saqlanib qolishi uchun
CREATE TABLE answer_options (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT  NOT NULL REFERENCES questions (id),
    text        TEXT    NOT NULL,
    is_correct  BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT     NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_answer_options_question ON answer_options (question_id);

CREATE TABLE test_sessions (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT           NOT NULL REFERENCES users (id),
    subject_id     BIGINT           NOT NULL REFERENCES subjects (id),
    mode           VARCHAR(20)      NOT NULL,
    question_count INT              NOT NULL,
    status         VARCHAR(20)      NOT NULL,
    started_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    correct_count  INT,
    score_percent  DOUBLE PRECISION
);
CREATE INDEX idx_test_sessions_user ON test_sessions (user_id);

CREATE TABLE test_session_topics (
    session_id BIGINT NOT NULL REFERENCES test_sessions (id) ON DELETE CASCADE,
    topic_id   BIGINT NOT NULL REFERENCES topics (id),
    PRIMARY KEY (session_id, topic_id)
);

CREATE TABLE session_questions (
    id                 BIGSERIAL PRIMARY KEY,
    session_id         BIGINT       NOT NULL REFERENCES test_sessions (id) ON DELETE CASCADE,
    question_id        BIGINT       NOT NULL REFERENCES questions (id),
    order_index        INT          NOT NULL,
    -- Aralashtirilgan variantlar tartibi (id lar vergul bilan), sahifa yangilanganda tartib o'zgarmasligi uchun
    option_order       VARCHAR(500) NOT NULL,
    selected_option_id BIGINT REFERENCES answer_options (id),
    is_correct         BOOLEAN,
    answered_at        TIMESTAMPTZ
);
CREATE INDEX idx_session_questions_session ON session_questions (session_id);
