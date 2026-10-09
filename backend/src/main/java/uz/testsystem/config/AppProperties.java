package uz.testsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, Seed seed) {

    public record Jwt(String secret, long expirationHours) {
    }

    public record Admin(String username, String password, String fullName) {
    }

    /** Ishga tushishda avtomatik yuklanadigan fanlar (import formatidagi JSON fayllar). */
    public record Seed(boolean enabled, String location) {
    }
}
