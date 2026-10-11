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

    /** Oddiy testda – tanlangan fan; blok testda {@code null}, guruhlar {@link #groups} da, sinflar {@link #subjects} da. */
    @ManyToOne(fetch = FetchType.LAZY)
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

    /** Blok test balli (yakunlangach). */
    private Double score;

    @Column(name = "max_score")
    private Double maxScore;

    /** Vaqt chegarasi (blok testda har bir fanga 1 soat); {@code null} – cheklanmagan. */
    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @ElementCollection
    @CollectionTable(name = "test_session_groups", joinColumns = @JoinColumn(name = "session_id"))
    @OrderBy("orderIndex ASC")
    private List<SessionGroup> groups = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "test_session_subjects", joinColumns = @JoinColumn(name = "session_id"))
    @OrderBy("orderIndex ASC")
    private List<SessionSubject> subjects = new ArrayList<>();

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

    public boolean isBlock() {
        return mode == TestMode.BLOCK;
    }

    /** Test qachongacha yechilishi mumkin; vaqt chegarasi bo'lmasa {@code null}. */
    public Instant deadline() {
        return timeLimitSeconds == null ? null : startedAt.plusSeconds(timeLimitSeconds);
    }

    /** Vaqti tugagan, lekin hali yakunlanmagan test. */
    public boolean isExpired(Instant now) {
        Instant deadline = deadline();
        return !isFinished() && deadline != null && !now.isBefore(deadline);
    }

    public boolean isFinished() {
        return status == TestStatus.FINISHED;
    }
}
