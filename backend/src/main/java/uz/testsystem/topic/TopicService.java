package uz.testsystem.topic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.common.ApiException;
import uz.testsystem.common.Counts;
import uz.testsystem.question.QuestionRepository;
import uz.testsystem.subject.Subject;
import uz.testsystem.subject.SubjectRepository;
import uz.testsystem.subject.SubjectService;
import uz.testsystem.topic.TopicDtos.TopicDto;
import uz.testsystem.topic.TopicDtos.TopicRequest;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final QuestionRepository questionRepository;
    private final SubjectService subjectService;

    /** Foydalanuvchi uchun: faol fanning faol mavzulari va ulardagi faol savollar soni. */
    @Transactional(readOnly = true)
    public List<TopicDto> listActiveForSubject(Long subjectId) {
        subjectService.getActive(subjectId);
        return toDtos(subjectId,
                topicRepository.findAllBySubjectIdAndActiveTrueOrderByOrderIndexAscNameAsc(subjectId), true);
    }

    @Transactional(readOnly = true)
    public List<TopicDto> listAllForSubject(Long subjectId) {
        return toDtos(subjectId, topicRepository.findAllBySubjectIdOrderByOrderIndexAscNameAsc(subjectId), false);
    }

    @Transactional
    public TopicDto create(TopicRequest req) {
        Subject subject = subjectRepository.findById(req.subjectId())
                .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
        String name = req.name().trim();
        if (topicRepository.existsBySubjectIdAndNameIgnoreCase(subject.getId(), name)) {
            throw ApiException.conflict("Bu fanda bunday nomli mavzu mavjud");
        }
        Topic topic = new Topic();
        topic.setSubject(subject);
        topic.setOrderIndex(req.orderIndex() != null ? req.orderIndex()
                : topicRepository.maxOrderIndex(subject.getId()) + 1);
        apply(topic, req, name);
        topicRepository.save(topic);
        return toDto(topic, 0);
    }

    @Transactional
    public TopicDto update(Long id, TopicRequest req) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> ApiException.notFound("Mavzu topilmadi"));
        if (!topic.getSubject().getId().equals(req.subjectId())) {
            throw ApiException.badRequest("Mavzuni boshqa fanga ko'chirib bo'lmaydi");
        }
        String name = req.name().trim();
        if (topicRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(topic.getSubject().getId(), name, id)) {
            throw ApiException.conflict("Bu fanda bunday nomli mavzu mavjud");
        }
        if (req.orderIndex() != null) {
            topic.setOrderIndex(req.orderIndex());
        }
        apply(topic, req, name);
        long count = Counts.toMap(questionRepository.countByTopicOfSubject(topic.getSubject().getId(), false))
                .getOrDefault(id, 0L);
        return toDto(topic, count);
    }

    @Transactional
    public void deactivate(Long id) {
        Topic topic = topicRepository.findById(id).orElseThrow(() -> ApiException.notFound("Mavzu topilmadi"));
        topic.setActive(false);
    }

    private void apply(Topic topic, TopicRequest req, String name) {
        topic.setName(name);
        topic.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        if (req.active() != null) {
            topic.setActive(req.active());
        }
    }

    private List<TopicDto> toDtos(Long subjectId, List<Topic> topics, boolean onlyActive) {
        Map<Long, Long> counts = Counts.toMap(questionRepository.countByTopicOfSubject(subjectId, onlyActive));
        return topics.stream().map(t -> toDto(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    private TopicDto toDto(Topic t, long questionCount) {
        return new TopicDto(t.getId(), t.getSubject().getId(), t.getName(), t.getDescription(),
                t.getOrderIndex(), t.isActive(), questionCount);
    }
}
