package uz.testsystem.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.user.Role;
import uz.testsystem.user.User;
import uz.testsystem.user.UserRepository;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = props.admin();
        if (userRepository.existsByUsernameIgnoreCase(admin.username())) {
            return;
        }
        User user = new User();
        user.setUsername(admin.username());
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setFullName(admin.fullName());
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.info("Default admin '{}' yaratildi", admin.username());
    }
}
