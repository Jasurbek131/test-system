package uz.testsystem.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.testsystem.question.AnswerOption;
import uz.testsystem.question.Question;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "session_questions")
@Getter
@Setter
@NoArgsConstructor
public class SessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private TestSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "option_order", nullable = false)
    private String optionOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private AnswerOption selectedOption;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "answered_at")
    private Instant answeredAt;

    public List<Long> optionIds() {
        return Arrays.stream(optionOrder.split(",")).map(Long::valueOf).toList();
    }

    public void setOptionIds(List<Long> ids) {
        this.optionOrder = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public boolean isAnswered() {
        return selectedOption != null;
    }
}
