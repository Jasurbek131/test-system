package uz.testsystem.subject;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class SubjectDtos {

    private SubjectDtos() {
    }

    /** {@code scoreWeight} – guruhning 1 savol balli (blok test). */
    public record SubjectDto(Long id, String name, String description, boolean active, Long groupId,
                             String groupName, double scoreWeight, long topicCount, long questionCount,
                             Instant createdAt) {
    }

    public record SubjectRequest(
            @NotBlank(message = "Fan nomi kiritilishi shart")
            @Size(max = 200, message = "Fan nomi juda uzun")
            String name,
            String description,
            Boolean active,
            @Size(max = 200, message = "Guruh nomi juda uzun")
            String groupName,
            @DecimalMin(value = "0.1", message = "Ball kamida 0.1 bo'lishi kerak")
            @DecimalMax(value = "100", message = "Ball 100 dan oshmasligi kerak")
            Double scoreWeight) {
    }
}
