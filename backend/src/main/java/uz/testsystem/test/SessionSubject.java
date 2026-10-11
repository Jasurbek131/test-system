package uz.testsystem.test;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uz.testsystem.subject.Subject;
import uz.testsystem.subject.SubjectGroup;

/**
 * Blok testda tanlangan fan (sinf) va u qaysi guruh hisobiga kirgani. Guruh test yaratilgan paytdagisi bilan
 * saqlanadi: admin fanni keyin boshqa guruhga o'tkazsa ham eski natija o'zgarmaydi.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class SessionSubject {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private SubjectGroup group;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    public SessionSubject(Subject subject, SubjectGroup group, int orderIndex) {
        this.subject = subject;
        this.group = group;
        this.orderIndex = orderIndex;
    }
}
