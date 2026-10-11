-- Fan guruhlari: blok testda bir nechta sinf fanlari (masalan 1-sinf va 2-sinf Matematika) bitta fan bo'lib chiqadi,
-- savollar soni, ball va vaqt guruh uchun umumiy. O'rganish rejimida fanlar avvalgidek alohida.
CREATE TABLE subject_groups (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(200)     NOT NULL UNIQUE,
    score_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

-- Guruh nomi = fan nomi "N-sinf" qismisiz: "1-sinf Matematika" -> "Matematika"
INSERT INTO subject_groups (name, score_weight)
SELECT min(g), max(w)
FROM (SELECT regexp_replace(name, '^\s*\d+\s*-\s*sinf\s+', '', 'i') AS g, score_weight AS w FROM subjects) x
GROUP BY lower(g);

ALTER TABLE subjects ADD COLUMN group_id BIGINT REFERENCES subject_groups (id);
UPDATE subjects s SET group_id = g.id
FROM subject_groups g
WHERE lower(g.name) = lower(regexp_replace(s.name, '^\s*\d+\s*-\s*sinf\s+', '', 'i'));
ALTER TABLE subjects ALTER COLUMN group_id SET NOT NULL;
CREATE INDEX idx_subjects_group ON subjects (group_id);
-- Ball endi guruhda
ALTER TABLE subjects DROP COLUMN score_weight;

-- Blok testdagi guruhlar: savollar soni va ball test boshlangan paytdagi qiymat
CREATE TABLE test_session_groups (
    session_id     BIGINT           NOT NULL REFERENCES test_sessions (id) ON DELETE CASCADE,
    group_id       BIGINT           NOT NULL REFERENCES subject_groups (id),
    order_index    INT              NOT NULL,
    question_count INT              NOT NULL,
    score_weight   DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (session_id, group_id)
);
INSERT INTO test_session_groups (session_id, group_id, order_index, question_count, score_weight)
SELECT tss.session_id, s.group_id, min(tss.order_index), sum(tss.question_count), max(tss.score_weight)
FROM test_session_subjects tss
JOIN subjects s ON s.id = tss.subject_id
GROUP BY tss.session_id, s.group_id;

-- test_session_subjects endi faqat tanlangan sinflar (fanlar) va ularning guruhi (natijani guruhlash uchun)
ALTER TABLE test_session_subjects ADD COLUMN group_id BIGINT REFERENCES subject_groups (id);
UPDATE test_session_subjects tss SET group_id = s.group_id FROM subjects s WHERE s.id = tss.subject_id;
ALTER TABLE test_session_subjects ALTER COLUMN group_id SET NOT NULL;
ALTER TABLE test_session_subjects DROP COLUMN question_count;
ALTER TABLE test_session_subjects DROP COLUMN score_weight;
