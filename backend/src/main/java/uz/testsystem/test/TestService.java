package uz.testsystem.test;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.testsystem.common.ApiException;
import uz.testsystem.common.Counts;
import uz.testsystem.common.PageResponse;
import uz.testsystem.question.AnswerOption;
import uz.testsystem.question.Question;
import uz.testsystem.question.QuestionRepository;
import uz.testsystem.subject.Subject;
import uz.testsystem.subject.SubjectService;
import uz.testsystem.test.TestDtos.*;
import uz.testsystem.topic.Topic;
import uz.testsystem.topic.TopicRepository;
import uz.testsystem.user.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestService {

    private final Random random = new SecureRandom();

    private final TestSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final SubjectService subjectService;

    // ---------------------------------------------------------------- yaratish

    @Transactional
    public CreatedResponse create(Long userId, CreateTestRequest req) {
        Subject subject = subjectService.getActive(req.subjectId());

        Set<Long> topicIds = new LinkedHashSet<>(req.topicIds());
        List<Topic> topics = topicRepository.findAllByIdIn(topicIds);
        if (topics.size() != topicIds.size()) {
            throw ApiException.badRequest("Ba'zi mavzular topilmadi");
        }
        for (Topic t : topics) {
            if (!t.getSubject().getId().equals(subject.getId())) {
                throw ApiException.badRequest("Mavzular faqat tanlangan fanga tegishli bo'lishi kerak");
            }
            if (!t.isActive()) {
                throw ApiException.badRequest("Mavzu faol emas: " + t.getName());
            }
        }

        Map<Long, List<Long>> idsByTopic = new LinkedHashMap<>();
        for (Object[] row : questionRepository.findActiveIdsByTopicIds(topicIds)) {
            idsByTopic.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        int available = idsByTopic.values().stream().mapToInt(List::size).sum();
        if (available == 0) {
            throw ApiException.badRequest("Tanlangan mavzularda savollar yo'q");
        }
        if (req.questionCount() > available) {
            throw ApiException.badRequest("Tanlangan mavzularda faqat " + available + " ta savol bor");
        }

        List<Long> picked = QuestionPicker.pick(idsByTopic, req.questionCount(), random);
        TestSession session = newSession(userId, subject, req.mode() == null ? TestMode.LEARNING : req.mode());
        session.getTopics().addAll(topics);
        addQuestions(session, picked);
        sessionRepository.save(session);
        return new CreatedResponse(session.getId());
    }

    /** Yakunlangan testdagi xato va javobsiz savollardan yangi test yaratadi. */
    @Transactional
    public CreatedResponse retryWrong(Long userId, Long sessionId) {
        TestSession old = find(userId, sessionId);
        if (!old.isFinished()) {
            throw ApiException.badRequest("Avval testni yakunlang");
        }
        List<SessionQuestion> wrong = old.getQuestions().stream()
                .filter(sq -> !Boolean.TRUE.equals(sq.getCorrect()))
                .filter(sq -> sq.getQuestion().isActive())
                .toList();
        if (wrong.isEmpty()) {
            throw ApiException.badRequest("Bu testda xato javoblar yo'q");
        }
        List<Long> ids = new ArrayList<>(wrong.stream().map(sq -> sq.getQuestion().getId()).toList());
        Collections.shuffle(ids, random);

        TestSession session = newSession(userId, old.getSubject(), old.getMode());
        wrong.stream().map(sq -> sq.getQuestion().getTopic()).distinct().forEach(session.getTopics()::add);
        addQuestions(session, ids);
        sessionRepository.save(session);
        return new CreatedResponse(session.getId());
    }

    private TestSession newSession(Long userId, Subject subject, TestMode mode) {
        TestSession session = new TestSession();
        session.setUser(userRepository.getReferenceById(userId));
        session.setSubject(subject);
        session.setMode(mode);
        return session;
    }

    private void addQuestions(TestSession session, List<Long> questionIds) {
        Map<Long, Question> questions = questionRepository.findAllWithOptions(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        int index = 0;
        for (Long id : questionIds) {
            Question q = questions.get(id);
            List<Long> optionIds = new ArrayList<>(q.activeOptions().stream().map(AnswerOption::getId).toList());
            Collections.shuffle(optionIds, random);

            SessionQuestion sq = new SessionQuestion();
            sq.setQuestion(q);
            sq.setOrderIndex(index++);
            sq.setOptionIds(optionIds);
            session.addQuestion(sq);
        }
        session.setQuestionCount(questionIds.size());
    }

    // ---------------------------------------------------------------- yechish

    @Transactional(readOnly = true)
    public SessionView get(Long userId, Long sessionId) {
        TestSession s = find(userId, sessionId);
        List<QuestionView> questions = s.getQuestions().stream()
                .map(sq -> toView(sq, canReveal(s, sq)))
                .toList();
        return new SessionView(s.getId(), s.getSubject().getId(), s.getSubject().getName(), s.getMode(),
                s.getStatus(), s.getQuestionCount(), s.getStartedAt(), s.getFinishedAt(), s.getCorrectCount(),
                s.getScorePercent(), topicRefs(s), questions);
    }

    @Transactional
    public AnswerResponse answer(Long userId, Long sessionId, AnswerRequest req) {
        TestSession s = find(userId, sessionId);
        if (s.isFinished()) {
            throw ApiException.badRequest("Test yakunlangan");
        }
        SessionQuestion sq = s.getQuestions().stream()
                .filter(q -> q.getId().equals(req.sessionQuestionId()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Savol bu testga tegishli emas"));
        if (s.getMode() == TestMode.LEARNING && sq.isAnswered()) {
            throw ApiException.conflict("Bu savolga javob berilgan");
        }
        if (!sq.optionIds().contains(req.optionId())) {
            throw ApiException.badRequest("Variant bu savolga tegishli emas");
        }
        AnswerOption option = sq.getQuestion().getOptions().stream()
                .filter(o -> o.getId().equals(req.optionId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Variant topilmadi"));

        sq.setSelectedOption(option);
        sq.setCorrect(option.isCorrect());
        sq.setAnsweredAt(Instant.now());

        boolean reveal = canReveal(s, sq);
        return new AnswerResponse(sq.getId(), option.getId(), true,
                reveal ? sq.getCorrect() : null,
                reveal ? correctOptionId(sq) : null,
                reveal ? sq.getQuestion().getExplanation() : null);
    }

    @Transactional
    public ResultView finish(Long userId, Long sessionId) {
        TestSession s = find(userId, sessionId);
        if (!s.isFinished()) {
            int correct = (int) s.getQuestions().stream().filter(sq -> Boolean.TRUE.equals(sq.getCorrect())).count();
            s.setCorrectCount(correct);
            s.setScorePercent(percent(correct, s.getQuestionCount()));
            s.setStatus(TestStatus.FINISHED);
            s.setFinishedAt(Instant.now());
        }
        return buildResult(s);
    }

    @Transactional(readOnly = true)
    public ResultView result(Long userId, Long sessionId) {
        TestSession s = find(userId, sessionId);
        if (!s.isFinished()) {
            throw ApiException.badRequest("Test hali yakunlanmagan");
        }
        return buildResult(s);
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionSummary> history(Long userId, int page, int size) {
        var sessions = sessionRepository.findAllByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = sessions.getContent().stream().map(TestSession::getId).toList();
        Map<Long, Long> answered = ids.isEmpty() ? Map.of() : Counts.toMap(sessionRepository.countAnswered(ids));
        return PageResponse.of(sessions, s -> new SessionSummary(s.getId(), s.getSubject().getId(),
                s.getSubject().getName(), s.getMode(), s.getStatus(), s.getQuestionCount(),
                answered.getOrDefault(s.getId(), 0L), s.getCorrectCount(), s.getScorePercent(),
                s.getStartedAt(), s.getFinishedAt(), s.getTopics().stream().map(Topic::getName).toList()));
    }

    /** Tarixni tozalash: faqat foydalanuvchining testlari o'chadi, fan/mavzu/savollarga tegilmaydi. */
    @Transactional
    public void clearHistory(Long userId) {
        sessionRepository.deleteAllByUserId(userId);
    }

    // ---------------------------------------------------------------- yordamchi

    private ResultView buildResult(TestSession s) {
        List<SessionQuestion> qs = s.getQuestions();
        int correct = (int) qs.stream().filter(sq -> Boolean.TRUE.equals(sq.getCorrect())).count();
        int unanswered = (int) qs.stream().filter(sq -> !sq.isAnswered()).count();
        int wrong = qs.size() - correct - unanswered;

        Map<Topic, List<SessionQuestion>> byTopic = qs.stream()
                .collect(Collectors.groupingBy(sq -> sq.getQuestion().getTopic(), LinkedHashMap::new, Collectors.toList()));
        List<TopicResult> topicResults = byTopic.entrySet().stream()
                .map(e -> {
                    List<SessionQuestion> list = e.getValue();
                    int c = (int) list.stream().filter(sq -> Boolean.TRUE.equals(sq.getCorrect())).count();
                    int u = (int) list.stream().filter(sq -> !sq.isAnswered()).count();
                    return new TopicResult(e.getKey().getId(), e.getKey().getName(), list.size(), c,
                            list.size() - c - u, u, percent(c, list.size()));
                })
                .sorted(Comparator.comparingDouble(TopicResult::percent).thenComparing(TopicResult::topicName))
                .toList();

        long duration = Duration.between(s.getStartedAt(),
                s.getFinishedAt() != null ? s.getFinishedAt() : Instant.now()).toSeconds();
        return new ResultView(s.getId(), s.getSubject().getId(), s.getSubject().getName(), s.getMode(),
                s.getQuestionCount(), correct, wrong, unanswered, percent(correct, s.getQuestionCount()),
                s.getStartedAt(), s.getFinishedAt(), duration, topicResults,
                qs.stream().map(sq -> toView(sq, true)).toList());
    }

    /** O'rganish rejimida javob berilgan savol, yoki yakunlangan test – to'g'ri javob ko'rsatilishi mumkin. */
    private boolean canReveal(TestSession s, SessionQuestion sq) {
        return s.isFinished() || (s.getMode() == TestMode.LEARNING && sq.isAnswered());
    }

    private QuestionView toView(SessionQuestion sq, boolean reveal) {
        Question q = sq.getQuestion();
        Map<Long, AnswerOption> optionsById = q.getOptions().stream()
                .collect(Collectors.toMap(AnswerOption::getId, Function.identity()));
        List<OptionView> options = sq.optionIds().stream()
                .map(optionsById::get)
                .filter(Objects::nonNull)
                .map(o -> new OptionView(o.getId(), o.getText()))
                .toList();
        return new QuestionView(sq.getId(), sq.getOrderIndex(), q.getTopic().getId(), q.getTopic().getName(),
                q.getText(), q.getDifficulty(), options,
                sq.getSelectedOption() != null ? sq.getSelectedOption().getId() : null,
                sq.isAnswered(),
                reveal ? sq.getCorrect() : null,
                reveal ? correctOptionId(sq) : null,
                reveal ? q.getExplanation() : null);
    }

    private Long correctOptionId(SessionQuestion sq) {
        Set<Long> shown = new HashSet<>(sq.optionIds());
        return sq.getQuestion().getOptions().stream()
                .filter(o -> o.isCorrect() && shown.contains(o.getId()))
                .map(AnswerOption::getId)
                .findFirst()
                .orElse(null);
    }

    private List<TopicRef> topicRefs(TestSession s) {
        return s.getTopics().stream().map(t -> new TopicRef(t.getId(), t.getName())).toList();
    }

    private TestSession find(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Test topilmadi"));
    }

    static double percent(int part, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }
}
