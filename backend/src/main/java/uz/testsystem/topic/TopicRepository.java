package uz.testsystem.topic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findAllBySubjectIdOrderByOrderIndexAscNameAsc(Long subjectId);

    List<Topic> findAllBySubjectIdAndActiveTrueOrderByOrderIndexAscNameAsc(Long subjectId);

    Optional<Topic> findBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(Long subjectId, String name, Long id);

    boolean existsBySubjectIdAndNameIgnoreCase(Long subjectId, String name);

    @Query("select coalesce(max(t.orderIndex), 0) from Topic t where t.subject.id = :subjectId")
    int maxOrderIndex(Long subjectId);

    List<Topic> findAllByIdIn(Collection<Long> ids);

    /** Har bir fandagi mavzular soni: [subjectId, count] */
    @Query("""
            select t.subject.id, count(t) from Topic t
            where (:onlyActive = false or t.active = true)
            group by t.subject.id
            """)
    List<Object[]> countBySubject(boolean onlyActive);
}
