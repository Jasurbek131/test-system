package uz.testsystem.test;

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

    public record AnswerRequest(
            @NotNull(message = "Savol ko'rsatilishi shart") Long sessionQuestionId,
            @NotNull(message = "Variant tanlanishi shart") Long optionId) {
    }

    public record CreatedResponse(Long id) {
    }

    public record TopicRef(Long id, String name) {
    }

    public record OptionView(Long id, String text) {
    }

    /**
     * Sessiyadagi savol. {@code correct}, {@code correctOptionId} va {@code explanation}
     * faqat ruxsat etilgan holatda to'ldiriladi (o'rganish rejimida javobdan keyin yoki test yakunlangach).
     */
    public record QuestionView(Long id, int orderIndex, Long topicId, String topicName, String text,
                               Difficulty difficulty, List<OptionView> options, Long selectedOptionId,
                               boolean answered, Boolean correct, Long correctOptionId, String explanation) {
    }

    public record SessionView(Long id, Long subjectId, String subjectName, TestMode mode, TestStatus status,
                              int questionCount, Instant startedAt, Instant finishedAt,
                              Integer correctCount, Double scorePercent, List<TopicRef> topics,
                              List<QuestionView> questions) {
    }

    public record AnswerResponse(Long sessionQuestionId, Long selectedOptionId, boolean answered,
                                 Boolean correct, Long correctOptionId, String explanation) {
    }

    public record TopicResult(Long topicId, String topicName, int total, int correct, int wrong,
                              int unanswered, double percent) {
    }

    public record ResultView(Long id, Long subjectId, String subjectName, TestMode mode,
                             int questionCount, int correctCount, int wrongCount, int unansweredCount,
                             double scorePercent, Instant startedAt, Instant finishedAt, long durationSeconds,
                             List<TopicResult> byTopic, List<QuestionView> questions) {
    }

    public record SessionSummary(Long id, Long subjectId, String subjectName, TestMode mode, TestStatus status,
                                 int questionCount, long answeredCount, Integer correctCount, Double scorePercent,
                                 Instant startedAt, Instant finishedAt, List<String> topicNames) {
    }
}
