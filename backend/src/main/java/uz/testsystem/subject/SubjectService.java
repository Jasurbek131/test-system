package uz.testsystem.subject;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.common.ApiException;
import uz.testsystem.common.Counts;
import uz.testsystem.question.QuestionRepository;
import uz.testsystem.subject.SubjectDtos.SubjectDto;
import uz.testsystem.subject.SubjectDtos.SubjectRequest;
import uz.testsystem.topic.TopicRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<SubjectDto> listActive() {
        return toDtos(subjectRepository.findAllByActiveTrueOrderByNameAsc(), true);
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> listAll() {
        return toDtos(subjectRepository.findAllByOrderByNameAsc(), false);
    }

    @Transactional(readOnly = true)
    public Subject getActive(Long id) {
        return subjectRepository.findById(id).filter(Subject::isActive)
                .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
    }

    @Transactional
    public SubjectDto create(SubjectRequest req) {
        String name = req.name().trim();
        if (subjectRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Bunday nomli fan mavjud");
        }
        Subject subject = new Subject();
        apply(subject, req, name);
        subjectRepository.save(subject);
        return toDto(subject, 0, 0);
    }

    @Transactional
    public SubjectDto update(Long id, SubjectRequest req) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
        String name = req.name().trim();
        if (subjectRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("Bunday nomli fan mavjud");
        }
        apply(subject, req, name);
        return toDtos(List.of(subject), false).getFirst();
    }

    @Transactional
    public void deactivate(Long id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
        subject.setActive(false);
    }

    private void apply(Subject subject, SubjectRequest req, String name) {
        subject.setName(name);
        subject.setDescription(blankToNull(req.description()));
        if (req.active() != null) {
            subject.setActive(req.active());
        }
    }

    private List<SubjectDto> toDtos(List<Subject> subjects, boolean onlyActive) {
        Map<Long, Long> topics = Counts.toMap(topicRepository.countBySubject(onlyActive));
        Map<Long, Long> questions = Counts.toMap(questionRepository.countBySubject(onlyActive));
        return subjects.stream()
                .map(s -> toDto(s, topics.getOrDefault(s.getId(), 0L), questions.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    private SubjectDto toDto(Subject s, long topicCount, long questionCount) {
        return new SubjectDto(s.getId(), s.getName(), s.getDescription(), s.isActive(),
                topicCount, questionCount, s.getCreatedAt());
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
