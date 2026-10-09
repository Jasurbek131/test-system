package uz.testsystem.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uz.testsystem.topic.TopicDtos.TopicDto;
import uz.testsystem.topic.TopicDtos.TopicRequest;
import uz.testsystem.topic.TopicService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/topics")
@RequiredArgsConstructor
public class AdminTopicController {

    private final TopicService topicService;

    @GetMapping
    public List<TopicDto> list(@RequestParam Long subjectId) {
        return topicService.listAllForSubject(subjectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDto create(@Valid @RequestBody TopicRequest req) {
        return topicService.create(req);
    }

    @PutMapping("/{id}")
    public TopicDto update(@PathVariable Long id, @Valid @RequestBody TopicRequest req) {
        return topicService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        topicService.deactivate(id);
    }
}
