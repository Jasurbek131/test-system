package uz.testsystem.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    /** Har bir mavzudagi faol savollar soni: [topicId, count] */
    @Query("""
            select q.topic.id, count(q) from Question q
            where q.topic.id in :topicIds and q.active = true
            group by q.topic.id
            """)
    List<Object[]> countActiveByTopicIds(Collection<Long> topicIds);

    /** Testga tanlash uchun: [topicId, questionId, difficulty] */
    @Query("""
            select q.topic.id, q.id, q.difficulty from Question q
            where q.topic.id in :topicIds and q.active = true
            """)
    List<Object[]> findActiveIdsByTopicIds(Collection<Long> topicIds);

    /** Har bir fandagi savollar soni: [subjectId, count] */
    @Query("""
            select q.topic.subject.id, count(q) from Question q
            where (:onlyActive = false or (q.active = true and q.topic.active = true))
            group by q.topic.subject.id
            """)
    List<Object[]> countBySubject(boolean onlyActive);

    /** Mavzular bo'yicha savollar soni: [topicId, count] */
    @Query("""
            select q.topic.id, count(q) from Question q
            where q.topic.subject.id = :subjectId and (:onlyActive = false or q.active = true)
            group by q.topic.id
            """)
    List<Object[]> countByTopicOfSubject(Long subjectId, boolean onlyActive);

    @Query("""
            select distinct q from Question q left join fetch q.options
            where q.id in :ids
            """)
    List<Question> findAllWithOptions(Collection<Long> ids);
}
