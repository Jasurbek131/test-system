package uz.testsystem.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.testsystem.subject.SubjectDtos.SubjectDto;
import uz.testsystem.subject.SubjectDtos.SubjectRequest;
import uz.testsystem.subject.SubjectService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public List<SubjectDto> list() {
        return subjectService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectDto create(@Valid @RequestBody SubjectRequest req) {
        return subjectService.create(req);
    }

    @PutMapping("/{id}")
    public SubjectDto update(@PathVariable Long id, @Valid @RequestBody SubjectRequest req) {
        return subjectService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        subjectService.deactivate(id);
    }
}
