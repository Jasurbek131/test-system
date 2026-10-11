package uz.testsystem.subject;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SubjectGroupService {

    private static final Pattern GRADE_PREFIX = Pattern.compile("^\\s*\\d+\\s*-\\s*sinf\\s+", Pattern.CASE_INSENSITIVE);

    private final SubjectGroupRepository groupRepository;

    /** Fan nomidan standart guruh nomi: "1-sinf Matematika" -> "Matematika" (V4 migratsiyadagi qoida bilan bir xil). */
    public static String defaultGroupName(String subjectName) {
        String name = GRADE_PREFIX.matcher(subjectName.trim()).replaceFirst("").trim();
        return name.isEmpty() ? subjectName.trim() : name;
    }

    /** Guruhni nomi bo'yicha topadi, bo'lmasa yaratadi. {@code scoreWeight} faqat yangi guruhga qo'llanadi. */
    @Transactional
    public SubjectGroup findOrCreate(String name, Double scoreWeight) {
        String trimmed = name.trim();
        return groupRepository.findByNameIgnoreCase(trimmed).orElseGet(() -> {
            SubjectGroup group = new SubjectGroup();
            group.setName(trimmed);
            if (scoreWeight != null && scoreWeight > 0) {
                group.setScoreWeight(scoreWeight);
            }
            return groupRepository.save(group);
        });
    }
}
