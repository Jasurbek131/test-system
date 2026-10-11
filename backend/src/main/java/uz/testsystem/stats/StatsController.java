package uz.testsystem.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.testsystem.security.AuthUser;
import uz.testsystem.stats.StatsDtos.MyStats;
import uz.testsystem.test.TestService;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final TestService testService;

    @GetMapping("/me")
    public MyStats me(@AuthenticationPrincipal AuthUser user) {
        testService.finishExpired(user.id());
        return statsService.myStats(user.id());
    }
}
