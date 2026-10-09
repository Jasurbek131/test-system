package uz.testsystem.security;

import uz.testsystem.user.Role;

/** JWT dan olingan joriy foydalanuvchi (SecurityContext principal). */
public record AuthUser(Long id, String username, Role role) {
}
