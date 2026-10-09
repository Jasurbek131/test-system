package uz.testsystem.subject;

import lombok.RequiredArgsConstructor;
import uz.testsystem.common.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.testsystem.subject.SubjectDtos.SubjectDto;
import uz.testsystem.topic.TopicDtos.TopicDto;
import uz.testsystem.topic.TopicService;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;
    private final TopicService topicService;

    @GetMapping
    public List<SubjectDto> list() {
        return subjectService.listActive();
    }

    @GetMapping("/{id}")
    public SubjectDto get(@PathVariable Long id) {
        return subjectService.listActive().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
    }

    @GetMapping("/{id}/topics")
    public List<TopicDto> topics(@PathVariable Long id) {
        return topicService.listActiveForSubject(id);
    }
}
