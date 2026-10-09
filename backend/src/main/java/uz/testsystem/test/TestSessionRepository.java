package uz.testsystem.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestSessionRepository extends JpaRepository<TestSession, Long> {

    Optional<TestSession> findByIdAndUserId(Long id, Long userId);

    Page<TestSession> findAllByUserId(Long userId, Pageable pageable);

    /** Sessiyalardagi javob berilgan savollar soni: [sessionId, count] */
    @Query("""
            select sq.session.id, count(sq) from SessionQuestion sq
            where sq.session.id in :sessionIds and sq.selectedOption is not null
            group by sq.session.id
            """)
    List<Object[]> countAnswered(Collection<Long> sessionIds);
}
