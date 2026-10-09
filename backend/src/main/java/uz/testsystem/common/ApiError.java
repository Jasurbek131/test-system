package uz.testsystem.common;

import java.time.Instant;
import java.util.List;

public record ApiError(Instant timestamp, int status, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String message) {
        return new ApiError(Instant.now(), status, message, List.of());
    }

    public static ApiError of(int status, String message, List<FieldError> errors) {
        return new ApiError(Instant.now(), status, message, errors);
    }
}
