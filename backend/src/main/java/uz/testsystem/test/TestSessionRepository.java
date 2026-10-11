package uz.testsystem.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Foydalanuvchining barcha sessiyalarini o'chiradi. session_questions va test_session_topics bazada ON DELETE CASCADE bilan o'chadi. */
    @Modifying
    @Query("delete from TestSession s where s.user.id = :userId")
    int deleteAllByUserId(Long userId);
}
