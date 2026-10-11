package uz.testsystem.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.common.ApiException;
import uz.testsystem.importer.ImportDtos.*;
import uz.testsystem.question.AnswerOption;
import uz.testsystem.question.Difficulty;
import uz.testsystem.question.Question;
import uz.testsystem.question.QuestionDtos.OptionRequest;
import uz.testsystem.question.QuestionRepository;
import uz.testsystem.question.QuestionService;
import uz.testsystem.subject.Subject;
import uz.testsystem.subject.SubjectGroupService;
import uz.testsystem.subject.SubjectRepository;
import uz.testsystem.topic.Topic;
import uz.testsystem.topic.TopicRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final SubjectRepository subjectRepository;
    private final SubjectGroupService groupService;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    /**
     * To'g'ri savollar import qilinadi, xatolilari o'tkazib yuboriladi va hisobotda ko'rsatiladi.
     */
    @Transactional
    public ImportReport importQuestions(ImportRequest req) {
        if (req == null || req.subject() == null || req.subject().isBlank()) {
            throw ApiException.badRequest("Fan nomi (subject) kiritilishi shart");
        }
        if (req.topics() == null || req.topics().isEmpty()) {
            throw ApiException.badRequest("Kamida bitta mavzu (topics) bo'lishi kerak");
        }

        String subjectName = req.subject().trim();
        boolean subjectCreated = false;
        Subject subject = subjectRepository.findByNameIgnoreCase(subjectName).orElse(null);
        if (subject == null) {
            subject = new Subject();
            subject.setName(subjectName);
            subject.setDescription(blankToNull(req.subjectDescription()));
            String groupName = req.group() == null || req.group().isBlank()
                    ? SubjectGroupService.defaultGroupName(subjectName) : req.group();
            subject.setGroup(groupService.findOrCreate(groupName, req.scoreWeight()));
            subjectRepository.save(subject);
            subjectCreated = true;
        }

        List<ImportError> errors = new ArrayList<>();
        int topicsCreated = 0;
        int imported = 0;
        int skipped = 0;
        int nextOrder = topicRepository.maxOrderIndex(subject.getId()) + 1;

        for (int ti = 0; ti < req.topics().size(); ti++) {
            ImportTopic it = req.topics().get(ti);
            String topicPath = "topics[" + ti + "]";
            if (it == null || it.name() == null || it.name().isBlank()) {
                errors.add(new ImportError(topicPath, "Mavzu nomi kiritilmagan"));
                skipped += it == null || it.questions() == null ? 0 : it.questions().size();
                continue;
            }
            Topic topic = topicRepository.findBySubjectIdAndNameIgnoreCase(subject.getId(), it.name().trim())
                    .orElse(null);
            if (topic == null) {
                topic = new Topic();
                topic.setSubject(subject);
                topic.setName(it.name().trim());
                topic.setDescription(blankToNull(it.description()));
                topic.setOrderIndex(nextOrder++);
                topicRepository.save(topic);
                topicsCreated++;
            }
            if (it.questions() == null) {
                continue;
            }
            for (int qi = 0; qi < it.questions().size(); qi++) {
                ImportQuestion iq = it.questions().get(qi);
                String path = topicPath + ".questions[" + qi + "]";
                String error = validate(iq);
                if (error != null) {
                    errors.add(new ImportError(path, error));
                    skipped++;
                    continue;
                }
                Question q = new Question();
                q.setTopic(topic);
                q.setText(iq.text().trim());
                q.setExplanation(blankToNull(iq.explanation()));
                q.setDifficulty(iq.difficulty() == null ? Difficulty.MEDIUM : iq.difficulty());
                for (int oi = 0; oi < iq.options().size(); oi++) {
                    ImportOption o = iq.options().get(oi);
                    q.addOption(new AnswerOption(o.text().trim(), o.correct(), oi));
                }
                questionRepository.save(q);
                imported++;
            }
        }
        return new ImportReport(subject.getId(), subjectCreated, topicsCreated, imported, skipped, errors);
    }

    private String validate(ImportQuestion q) {
        if (q == null || q.text() == null || q.text().isBlank()) {
            return "Savol matni kiritilmagan";
        }
        if (q.options() == null) {
            return "Variantlar kiritilmagan";
        }
        if (q.options().stream().anyMatch(o -> o == null || o.text() == null || o.text().isBlank())) {
            return "Variant matni bo'sh";
        }
        try {
            QuestionService.validateOptions(q.options().stream()
                    .map(o -> new OptionRequest(null, o.text(), o.correct())).toList());
        } catch (ApiException e) {
            return e.getMessage();
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
