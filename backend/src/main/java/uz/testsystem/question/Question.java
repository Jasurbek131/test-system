package uz.testsystem.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.testsystem.topic.Topic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(nullable = false)
    private String text;

    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC, id ASC")
    private List<AnswerOption> options = new ArrayList<>();

    public List<AnswerOption> activeOptions() {
        return options.stream().filter(AnswerOption::isActive).toList();
    }

    public void addOption(AnswerOption option) {
        option.setQuestion(this);
        options.add(option);
    }
}
