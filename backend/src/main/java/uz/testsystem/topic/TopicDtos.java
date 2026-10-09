package uz.testsystem.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TopicDtos {

    private TopicDtos() {
    }

    public record TopicDto(Long id, Long subjectId, String name, String description,
                           int orderIndex, boolean active, long questionCount) {
    }

    public record TopicRequest(
            @NotNull(message = "Fan tanlanishi shart") Long subjectId,
            @NotBlank(message = "Mavzu nomi kiritilishi shart")
            @Size(max = 200, message = "Mavzu nomi juda uzun")
            String name,
            String description,
            Integer orderIndex,
            Boolean active) {
    }
}
