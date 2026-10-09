package uz.testsystem.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.auth.AuthDtos.AuthResponse;
import uz.testsystem.auth.AuthDtos.LoginRequest;
import uz.testsystem.auth.AuthDtos.RegisterRequest;
import uz.testsystem.auth.AuthDtos.UserDto;
import uz.testsystem.common.ApiException;
import uz.testsystem.security.JwtService;
import uz.testsystem.user.Role;
import uz.testsystem.user.User;
import uz.testsystem.user.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String username = req.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("Bu login band");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setRole(Role.USER);
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsernameIgnoreCase(req.username().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        return new AuthResponse(jwtService.generate(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return userRepository.findById(userId).map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound("Foydalanuvchi topilmadi"));
    }
}
