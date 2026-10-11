package uz.testsystem.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.stats.StatsDtos.MyStats;
import uz.testsystem.stats.StatsDtos.SubjectStat;
import uz.testsystem.stats.StatsDtos.TopicStat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    /**
     * Statistikaga yakunlangan testlar va o'rganish rejimidagi javoblar kiradi.
     * Davom etayotgan imtihon rejimidagi javoblar kirmaydi (natija oldindan ma'lum bo'lib qolmasligi uchun).
     */
    private static final String COUNTED_ANSWERS = """
            from session_questions sq
            join test_sessions s on s.id = sq.session_id
            join questions q on q.id = sq.question_id
            join topics t on t.id = q.topic_id
            join subjects sub on sub.id = t.subject_id
            where s.user_id = :userId
              and sq.selected_option_id is not null
              and (s.status = 'FINISHED' or s.mode = 'LEARNING')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public MyStats myStats(Long userId) {
        Map<String, Object> params = Map.of("userId", userId);

        Map<String, Object> totals = jdbc.queryForMap("""
                select count(*) as total,
                       count(*) filter (where status = 'FINISHED') as finished,
                       coalesce(avg(score_percent) filter (where status = 'FINISHED'), 0) as avg_score
                from test_sessions where user_id = :userId
                """, params);

        Map<String, Object> answers = jdbc.queryForMap(
                "select count(*) as answered, count(*) filter (where sq.is_correct) as correct " + COUNTED_ANSWERS,
                params);

        Map<Long, Long> testsBySubject = new HashMap<>();
        // Oddiy testda fan test_sessions.subject_id da, blok testda – test_session_subjects da
        jdbc.query("""
                select subject_id, count(*) as c from (
                    select subject_id from test_sessions where user_id = :userId and subject_id is not null
                    union all
                    select tss.subject_id from test_session_subjects tss
                    join test_sessions s on s.id = tss.session_id
                    where s.user_id = :userId
                ) x group by subject_id
                """, params, rs -> {
                    testsBySubject.put(rs.getLong("subject_id"), rs.getLong("c"));
                });

        List<SubjectStat> bySubject = jdbc.query("""
                select sub.id, sub.name, count(*) as answered, count(*) filter (where sq.is_correct) as correct
                """ + COUNTED_ANSWERS + " group by sub.id, sub.name order by sub.name", params,
                (rs, i) -> {
                    long answered = rs.getLong("answered");
                    long correct = rs.getLong("correct");
                    long subjectId = rs.getLong("id");
                    return new SubjectStat(subjectId, rs.getString("name"),
                            testsBySubject.getOrDefault(subjectId, 0L), answered, correct, percent(correct, answered));
                });

        List<TopicStat> byTopic = jdbc.query("""
                select sub.id as subject_id, sub.name as subject_name, t.id as topic_id, t.name as topic_name,
                       count(*) as answered, count(*) filter (where sq.is_correct) as correct
                """ + COUNTED_ANSWERS + """
                 group by sub.id, sub.name, t.id, t.name, t.order_index
                 order by sub.name, t.order_index, t.name
                """, params,
                (rs, i) -> {
                    long answered = rs.getLong("answered");
                    long correct = rs.getLong("correct");
                    return new TopicStat(rs.getLong("subject_id"), rs.getString("subject_name"),
                            rs.getLong("topic_id"), rs.getString("topic_name"), answered, correct,
                            percent(correct, answered));
                });

        long answered = ((Number) answers.get("answered")).longValue();
        long correct = ((Number) answers.get("correct")).longValue();
        double avg = Math.round(((Number) totals.get("avg_score")).doubleValue() * 10) / 10.0;
        return new MyStats(((Number) totals.get("total")).longValue(), ((Number) totals.get("finished")).longValue(),
                answered, correct, percent(correct, answered), avg, bySubject, byTopic);
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
    }
}
