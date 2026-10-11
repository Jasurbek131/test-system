-- Blok test (DTM uslubida): har bir fanning 1 ta savoli uchun beriladigan ball
ALTER TABLE subjects ADD COLUMN score_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0;
UPDATE subjects SET score_weight = 3.1 WHERE name ILIKE '%biologiya%';
UPDATE subjects SET score_weight = 1.1 WHERE name ILIKE '%matematika%';

-- Blok testda bir nechta fan bo'ladi, shuning uchun subject_id bo'sh qoladi va fanlar alohida jadvalda saqlanadi
ALTER TABLE test_sessions ALTER COLUMN subject_id DROP NOT NULL;
ALTER TABLE test_sessions ADD COLUMN score DOUBLE PRECISION;
ALTER TABLE test_sessions ADD COLUMN max_score DOUBLE PRECISION;

-- score_weight test boshlangan paytdagi qiymat: admin keyin o'zgartirsa ham eski natijalar o'zgarmaydi
CREATE TABLE test_session_subjects (
    session_id     BIGINT           NOT NULL REFERENCES test_sessions (id) ON DELETE CASCADE,
    subject_id     BIGINT           NOT NULL REFERENCES subjects (id),
    order_index    INT              NOT NULL,
    question_count INT              NOT NULL,
    score_weight   DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (session_id, subject_id)
);
