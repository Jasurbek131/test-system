package uz.testsystem.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.testsystem.security.AuthUser;
import uz.testsystem.stats.StatsDtos.MyStats;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/me")
    public MyStats me(@AuthenticationPrincipal AuthUser user) {
        return statsService.myStats(user.id());
    }
}
