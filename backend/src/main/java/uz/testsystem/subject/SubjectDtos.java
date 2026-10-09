package uz.testsystem.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SubjectDtos {

    private SubjectDtos() {
    }

    public record SubjectDto(Long id, String name, String description, boolean active,
                             long topicCount, long questionCount, Instant createdAt) {
    }

    public record SubjectRequest(
            @NotBlank(message = "Fan nomi kiritilishi shart")
            @Size(max = 200, message = "Fan nomi juda uzun")
            String name,
            String description,
            Boolean active) {
    }
}
