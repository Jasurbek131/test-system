package uz.testsystem.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.testsystem.subject.SubjectGroup;

/** Blok testdagi fan guruhi: umumiy savollar soni va test boshlangan paytdagi 1 savol balli. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class SessionGroup {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private SubjectGroup group;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "score_weight", nullable = false)
    private double scoreWeight;

    public SessionGroup(SubjectGroup group, int orderIndex, int questionCount, double scoreWeight) {
        this.group = group;
        this.orderIndex = orderIndex;
        this.questionCount = questionCount;
        this.scoreWeight = scoreWeight;
    }
}
