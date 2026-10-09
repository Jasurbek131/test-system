package uz.testsystem.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uz.testsystem.user.Role;
import uz.testsystem.user.User;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Login kiritilishi shart") String username,
            @NotBlank(message = "Parol kiritilishi shart") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "Login kiritilishi shart")
            @Size(min = 3, max = 50, message = "Login 3–50 belgidan iborat bo'lishi kerak")
            @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Login faqat lotin harflari, raqamlar, _ va . dan iborat bo'lishi mumkin")
            String username,
            @NotBlank(message = "Parol kiritilishi shart")
            @Size(min = 6, max = 100, message = "Parol kamida 6 belgidan iborat bo'lishi kerak")
            String password,
            @NotBlank(message = "Ism kiritilishi shart")
            @Size(max = 150, message = "Ism juda uzun")
            String fullName) {
    }

    public record UserDto(Long id, String username, String fullName, Role role) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole());
        }
    }

    public record AuthResponse(String token, UserDto user) {
    }
}
