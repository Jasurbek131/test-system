package uz.testsystem.test;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import uz.testsystem.common.PageResponse;
import uz.testsystem.security.AuthUser;
import uz.testsystem.test.TestDtos.*;

@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateTestRequest req) {
        return testService.create(user.id(), req);
    }

    @GetMapping("/history")
    public PageResponse<SessionSummary> history(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return testService.history(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public SessionView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return testService.get(user.id(), id);
    }

    @PostMapping("/{id}/answers")
    public AnswerResponse answer(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                 @Valid @RequestBody AnswerRequest req) {
        return testService.answer(user.id(), id, req);
    }

    @PostMapping("/{id}/finish")
    public ResultView finish(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return testService.finish(user.id(), id);
    }

    @GetMapping("/{id}/result")
    public ResultView result(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return testService.result(user.id(), id);
    }

    @PostMapping("/{id}/retry-wrong")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse retryWrong(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return testService.retryWrong(user.id(), id);
    }
}
