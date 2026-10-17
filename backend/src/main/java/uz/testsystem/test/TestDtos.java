package uz.testsystem.test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import uz.testsystem.question.Difficulty;

import java.time.Instant;
import java.util.List;

public final class TestDtos {

    private TestDtos() {
    }

    public record CreateTestRequest(
            @NotNull(message = "Fan tanlanishi shart") Long subjectId,
            @NotEmpty(message = "Kamida bitta mavzu tanlanishi shart") List<@NotNull Long> topicIds,
            @NotNull(message = "Savollar soni kiritilishi shart")
            @Min(value = 1, message = "Savollar soni kamida 1 ta bo'lishi kerak")
            @Max(value = 500, message = "Savollar soni 500 tadan oshmasligi kerak")
            Integer questionCount,
            TestMode mode) {
    }

    /** Guruhdan tanlangan sinflar (fanlar) va guruh uchun umumiy savollar soni. */
    public record BlockGroupRequest(
            @NotNull(message = "Fan tanlanishi shart") Long groupId,
            @NotEmpty(message = "Kamida bitta sinf tanlanishi shart") List<@NotNull Long> subjectIds,
            @NotNull(message = "Savollar soni kiritilishi shart")
            @Min(value = 1, message = "Savollar soni kamida 1 ta bo'lishi kerak")
            @Max(value = 500, message = "Savollar soni 500 tadan oshmasligi kerak")
            Integer questionCount) {
    }

    public record CreateBlockTestRequest(
            @NotEmpty(message = "Kamida bitta fan tanlanishi shart") List<@NotNull @Valid BlockGroupRequest> groups) {
    }

    /** Kartochka rejimida {@code optionId} o'rniga {@code known} ("Bilardim" / "Bilmasdim") yuboriladi. */
    public record AnswerRequest(
            @NotNull(message = "Savol ko'rsatilishi shart") Long sessionQuestionId,
            Long optionId,
            Boolean known) {
    }

    /** Takrorlash testi: {@code DUE} – vaqti kelgan savollar, {@code MISTAKES} – xatolar daftaridagi savollar. */
    public enum ReviewKind {
        DUE, MISTAKES
    }

    public record CreateReviewRequest(
            @NotNull(message = "Fan tanlanishi shart") Long subjectId,
            @NotNull(message = "Takrorlash turi ko'rsatilishi shart") ReviewKind kind,
            @NotNull(message = "Savollar soni kiritilishi shart")
            @Min(value = 1, message = "Savollar soni kamida 1 ta bo'lishi kerak")
            @Max(value = 500, message = "Savollar soni 500 tadan oshmasligi kerak")
            Integer questionCount,
            TestMode mode) {
    }

    public record CreatedResponse(Long id) {
    }

    public record TopicRef(Long id, String name) {
    }

    public record OptionView(Long id, String text) {
    }

    /** Blok testdagi fan guruhi va undan tanlangan sinflar. */
    public record SessionGroupView(Long groupId, String groupName, List<String> subjectNames, int questionCount,
                                   double scoreWeight) {
    }

    /**
     * Sessiyadagi savol. {@code correct}, {@code correctOptionId} va {@code explanation}
     * faqat ruxsat etilgan holatda to'ldiriladi (o'rganish rejimida javobdan keyin yoki test yakunlangach).
     */
    /** {@code groupId}, {@code groupName} – blok testda savol qaysi fan bo'limiga tegishli; oddiy testda {@code null}. */
    public record QuestionView(Long id, int orderIndex, Long subjectId, String subjectName, Long groupId,
                               String groupName, Long topicId, String topicName, String text,
                               Difficulty difficulty, List<OptionView> options, Long selectedOptionId,
                               boolean answered, Boolean correct, Long correctOptionId, String explanation) {
    }

    /**
     * {@code timeLimitSeconds} – vaqt chegarasi (blok testda), {@code remainingSeconds} – server bo'yicha qolgan vaqt
     * (mijoz soati noto'g'ri bo'lsa ham to'g'ri hisoblanishi uchun).
     */
    public record SessionView(Long id, Long subjectId, String subjectName, TestMode mode, TestStatus status,
                              int questionCount, Instant startedAt, Instant finishedAt,
                              Integer correctCount, Double scorePercent, Integer timeLimitSeconds,
                              Long remainingSeconds, List<TopicRef> topics,
                              List<SessionGroupView> groups, List<QuestionView> questions) {
    }

    public record AnswerResponse(Long sessionQuestionId, Long selectedOptionId, boolean answered,
                                 Boolean correct, Long correctOptionId, String explanation) {
    }

    public record TopicResult(Long topicId, String topicName, int total, int correct, int wrong,
                              int unanswered, double percent) {
    }

    /** Blok test natijasida bitta fan guruhi: {@code score = correct × scoreWeight}. */
    public record GroupResult(Long groupId, String groupName, List<String> subjectNames, double scoreWeight,
                              int total, int correct, int wrong, int unanswered, double percent, double score,
                              double maxScore) {
    }

    /**
     * {@code score}, {@code maxScore} va {@code byGroup} faqat blok testda to'ldiriladi.
     * {@code xp} – shu testda olingan XP (bir savol 24 soatda bir marta XP beradi, shuning uchun serverda hisoblanadi).
     */
    public record ResultView(Long id, Long subjectId, String subjectName, TestMode mode,
                             int questionCount, int correctCount, int wrongCount, int unansweredCount,
                             double scorePercent, Double score, Double maxScore,
                             Instant startedAt, Instant finishedAt, long durationSeconds, Integer timeLimitSeconds,
                             List<GroupResult> byGroup, List<TopicResult> byTopic, List<QuestionView> questions,
                             int xp) {
    }

    public record SessionSummary(Long id, Long subjectId, String subjectName, TestMode mode, TestStatus status,
                                 int questionCount, long answeredCount, Integer correctCount, Double scorePercent,
                                 Double score, Double maxScore, Instant startedAt, Instant finishedAt,
                                 List<String> topicNames, List<String> groupLabels) {
    }
}
