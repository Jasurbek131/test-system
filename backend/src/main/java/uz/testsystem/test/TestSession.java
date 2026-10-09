package uz.testsystem.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.testsystem.subject.Subject;
import uz.testsystem.topic.Topic;
import uz.testsystem.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "test_sessions")
@Getter
@Setter
@NoArgsConstructor
public class TestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestMode mode;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestStatus status = TestStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "score_percent")
    private Double scorePercent;

    @ManyToMany
    @JoinTable(name = "test_session_topics",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id"))
    @OrderBy("orderIndex ASC")
    private Set<Topic> topics = new LinkedHashSet<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<SessionQuestion> questions = new ArrayList<>();

    public void addQuestion(SessionQuestion sq) {
        sq.setSession(this);
        questions.add(sq);
    }

    public boolean isFinished() {
        return status == TestStatus.FINISHED;
    }
}
