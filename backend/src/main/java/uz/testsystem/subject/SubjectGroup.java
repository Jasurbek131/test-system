package uz.testsystem.subject;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fan guruhi: blok testda bir nechta sinf fanlari (masalan 1-sinf va 2-sinf Matematika) bitta fan bo'lib chiqadi.
 * Savollar soni, ball va vaqt guruh uchun umumiy.
 */
@Entity
@Table(name = "subject_groups")
@Getter
@Setter
@NoArgsConstructor
public class SubjectGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Blok testda 1 ta to'g'ri javob uchun ball (DTM: biologiya 3.1, matematika 1.1). */
    @Column(name = "score_weight", nullable = false)
    private double scoreWeight = 1.0;
}
