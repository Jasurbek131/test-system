package uz.testsystem.subject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectGroupRepository extends JpaRepository<SubjectGroup, Long> {

    Optional<SubjectGroup> findByNameIgnoreCase(String name);
}
