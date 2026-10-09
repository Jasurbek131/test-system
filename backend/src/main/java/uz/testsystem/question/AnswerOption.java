package uz.testsystem.question;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "answer_options")
@Getter
@Setter
@NoArgsConstructor
public class AnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(nullable = false)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public AnswerOption(String text, boolean correct, int orderIndex) {
        this.text = text;
        this.correct = correct;
        this.orderIndex = orderIndex;
    }
}
