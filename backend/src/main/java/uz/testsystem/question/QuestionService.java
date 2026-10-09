package uz.testsystem.question;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.common.ApiException;
import uz.testsystem.common.PageResponse;
import uz.testsystem.question.QuestionDtos.OptionRequest;
import uz.testsystem.question.QuestionDtos.QuestionAdminDto;
import uz.testsystem.question.QuestionDtos.QuestionRequest;
import uz.testsystem.topic.Topic;
import uz.testsystem.topic.TopicRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;

    public record Filter(Long subjectId, Long topicId, Difficulty difficulty, Boolean active, String q) {
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionAdminDto> search(Filter f, Pageable pageable) {
        Specification<Question> spec = (root, query, cb) -> cb.conjunction();
        if (f.topicId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("topic").get("id"), f.topicId()));
        }
        if (f.subjectId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("topic").get("subject").get("id"), f.subjectId()));
        }
        if (f.difficulty() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("difficulty"), f.difficulty()));
        }
        if (f.active() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), f.active()));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("text")), like));
        }
        return PageResponse.of(questionRepository.findAll(spec, pageable), QuestionAdminDto::from);
    }

    @Transactional(readOnly = true)
    public QuestionAdminDto get(Long id) {
        return QuestionAdminDto.from(find(id));
    }

    @Transactional
    public QuestionAdminDto create(QuestionRequest req) {
        validateOptions(req.options());
        Question q = new Question();
        q.setTopic(findTopic(req.topicId()));
        applyFields(q, req);
        int i = 0;
        for (OptionRequest o : req.options()) {
            q.addOption(new AnswerOption(o.text().trim(), o.correct(), i++));
        }
        questionRepository.save(q);
        return QuestionAdminDto.from(q);
    }

    @Transactional
    public QuestionAdminDto update(Long id, QuestionRequest req) {
        validateOptions(req.options());
        Question q = find(id);
        if (!q.getTopic().getId().equals(req.topicId())) {
            Topic topic = findTopic(req.topicId());
            if (!topic.getSubject().getId().equals(q.getTopic().getSubject().getId())) {
                throw ApiException.badRequest("Savolni boshqa fanga ko'chirib bo'lmaydi");
            }
            q.setTopic(topic);
        }
        applyFields(q, req);

        // Variantlar id bo'yicha moslashtiriladi; so'rovda yo'qlari o'chirilmaydi, nofaol qilinadi
        // (eski test sessiyalaridagi javoblar buzilmasligi uchun).
        Map<Long, AnswerOption> existing = q.getOptions().stream()
                .collect(Collectors.toMap(AnswerOption::getId, Function.identity()));
        Set<Long> kept = new HashSet<>();
        int i = 0;
        for (OptionRequest o : req.options()) {
            AnswerOption option;
            if (o.id() != null) {
                option = existing.get(o.id());
                if (option == null || !option.isActive()) {
                    throw ApiException.badRequest("Variant topilmadi: " + o.id());
                }
                option.setText(o.text().trim());
                option.setCorrect(o.correct());
                option.setOrderIndex(i++);
                kept.add(o.id());
            } else {
                q.addOption(new AnswerOption(o.text().trim(), o.correct(), i++));
            }
        }
        existing.values().stream().filter(o -> !kept.contains(o.getId())).forEach(o -> o.setActive(false));
        questionRepository.flush();
        return QuestionAdminDto.from(q);
    }

    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    /** Variantlar orasida aynan bitta to'g'ri javob bo'lishi va matnlar takrorlanmasligi kerak. */
    public static void validateOptions(List<OptionRequest> options) {
        if (options == null || options.size() < 2 || options.size() > 6) {
            throw ApiException.badRequest("Variantlar soni 2 tadan 6 tagacha bo'lishi kerak");
        }
        long correct = options.stream().filter(OptionRequest::correct).count();
        if (correct != 1) {
            throw ApiException.badRequest("Aynan bitta variant to'g'ri deb belgilanishi kerak");
        }
        long distinct = options.stream().map(o -> o.text() == null ? "" : o.text().trim().toLowerCase())
                .distinct().count();
        if (distinct != options.size()) {
            throw ApiException.badRequest("Variantlar matni takrorlanmasligi kerak");
        }
    }

    private void applyFields(Question q, QuestionRequest req) {
        q.setText(req.text().trim());
        q.setExplanation(req.explanation() == null || req.explanation().isBlank() ? null : req.explanation().trim());
        if (req.difficulty() != null) {
            q.setDifficulty(req.difficulty());
        }
        if (req.active() != null) {
            q.setActive(req.active());
        }
    }

    private Question find(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Savol topilmadi"));
    }

    private Topic findTopic(Long id) {
        return topicRepository.findById(id).orElseThrow(() -> ApiException.notFound("Mavzu topilmadi"));
    }
}
