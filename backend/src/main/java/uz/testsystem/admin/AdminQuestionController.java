package uz.testsystem.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.testsystem.common.PageResponse;
import uz.testsystem.question.Difficulty;
import uz.testsystem.question.QuestionDtos.QuestionAdminDto;
import uz.testsystem.question.QuestionDtos.QuestionRequest;
import uz.testsystem.question.QuestionService;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final QuestionService questionService;

    @GetMapping
    public PageResponse<QuestionAdminDto> search(@RequestParam(required = false) Long subjectId,
                                                 @RequestParam(required = false) Long topicId,
                                                 @RequestParam(required = false) Difficulty difficulty,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "id"));
        return questionService.search(new QuestionService.Filter(subjectId, topicId, difficulty, active, q), pageable);
    }

    @GetMapping("/{id}")
    public QuestionAdminDto get(@PathVariable Long id) {
        return questionService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAdminDto create(@Valid @RequestBody QuestionRequest req) {
        return questionService.create(req);
    }

    @PutMapping("/{id}")
    public QuestionAdminDto update(@PathVariable Long id, @Valid @RequestBody QuestionRequest req) {
        return questionService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        questionService.deactivate(id);
    }
}
