package uz.testsystem.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record OptionRequest(
            Long id,
            @NotBlank(message = "Variant matni kiritilishi shart") String text,
            boolean correct) {
    }

    public record QuestionRequest(
            @NotNull(message = "Mavzu tanlanishi shart") Long topicId,
            @NotBlank(message = "Savol matni kiritilishi shart") String text,
            String explanation,
            Difficulty difficulty,
            Boolean active,
            @NotNull(message = "Variantlar kiritilishi shart")
            @Size(min = 2, max = 6, message = "Variantlar soni 2 tadan 6 tagacha bo'lishi kerak")
            List<@Valid OptionRequest> options) {
    }

    public record OptionDto(Long id, String text, boolean correct) {
    }

    public record QuestionAdminDto(Long id, Long subjectId, String subjectName, Long topicId, String topicName,
                                   String text, String explanation, Difficulty difficulty, boolean active,
                                   Instant createdAt, List<OptionDto> options) {

        public static QuestionAdminDto from(Question q) {
            var topic = q.getTopic();
            return new QuestionAdminDto(q.getId(), topic.getSubject().getId(), topic.getSubject().getName(),
                    topic.getId(), topic.getName(), q.getText(), q.getExplanation(), q.getDifficulty(),
                    q.isActive(), q.getCreatedAt(),
                    q.activeOptions().stream().map(o -> new OptionDto(o.getId(), o.getText(), o.isCorrect())).toList());
        }
    }
}
