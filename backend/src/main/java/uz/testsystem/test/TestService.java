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
import uz.testsystem.subject.SubjectGroup;
import uz.testsystem.subject.SubjectGroupRepository;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestService {

    /** Bitta testdagi savollar soni chegarasi (oddiy test uchun DTO validatsiyasi bilan bir xil). */
    static final int MAX_QUESTIONS = 500;

    /** Blok testda har bir fan (guruh) ga beriladigan vaqt (DTM'dagidek 1 soat). */
    static final int BLOCK_SECONDS_PER_SUBJECT = 3600;

    private final Random random = new SecureRandom();

    private final TestSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final SubjectService subjectService;
    private final SubjectGroupRepository groupRepository;

    // ---------------------------------------------------------------- yaratish

    @Transactional
    public CreatedResponse create(Long userId, CreateTestRequest req) {
        if (req.mode() == TestMode.BLOCK) {
            throw ApiException.badRequest("Blok test uchun fanlarni alohida tanlang");
        }
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

        Map<Long, List<Long>> idsByTopic = activeQuestionIdsByTopic(topicIds);
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

    /**
     * Blok test (DTM uslubida): har bir fan guruhidan (masalan Matematika) tanlangan sinflar bo'yicha umumiy sondagi
     * savol olinadi – avval sinflar orasida, keyin har bir sinf ichida mavzular bo'yicha teng taqsimlab. Savollar
     * fanlar bo'yicha alohida bo'limlarda ketma-ket turadi (avval 1-fan, keyin 2-fan), bo'lim ichida aralash.
     * Guruhning 1 savol balli shu paytdagi qiymati bilan saqlanadi.
     */
    @Transactional
    public CreatedResponse createBlock(Long userId, CreateBlockTestRequest req) {
        Set<Long> seen = new HashSet<>();
        int total = 0;
        for (BlockGroupRequest bg : req.groups()) {
            if (!seen.add(bg.groupId())) {
                throw ApiException.badRequest("Fanlar takrorlanmasligi kerak");
            }
            total += bg.questionCount();
        }
        if (total > MAX_QUESTIONS) {
            throw ApiException.badRequest("Bitta testda savollar soni " + MAX_QUESTIONS + " tadan oshmasligi kerak");
        }

        TestSession session = newSession(userId, null, TestMode.BLOCK);
        List<Long> picked = new ArrayList<>();
        int groupOrder = 0;
        int subjectOrder = 0;
        for (BlockGroupRequest bg : req.groups()) {
            SubjectGroup group = groupRepository.findById(bg.groupId())
                    .orElseThrow(() -> ApiException.notFound("Fan topilmadi"));
            Map<Subject, Map<Long, List<Long>>> idsBySubject = new LinkedHashMap<>();
            for (Long subjectId : new LinkedHashSet<>(bg.subjectIds())) {
                Subject subject = subjectService.getActive(subjectId);
                if (!subject.getGroup().getId().equals(group.getId())) {
                    throw ApiException.badRequest(subject.getName() + " – " + group.getName() + " faniga tegishli emas");
                }
                List<Long> topicIds = topicRepository.findAllBySubjectIdAndActiveTrueOrderByOrderIndexAscNameAsc(subjectId)
                        .stream().map(Topic::getId).toList();
                idsBySubject.put(subject, activeQuestionIdsByTopic(topicIds));
            }
            Map<Subject, Integer> available = new LinkedHashMap<>();
            idsBySubject.forEach((subject, byTopic) ->
                    available.put(subject, byTopic.values().stream().mapToInt(List::size).sum()));
            int availableTotal = available.values().stream().mapToInt(Integer::intValue).sum();
            if (bg.questionCount() > availableTotal) {
                throw ApiException.badRequest(group.getName() + ": tanlangan sinflarda faqat " + availableTotal + " ta savol bor");
            }
            List<Long> section = new ArrayList<>();
            QuestionPicker.allocate(available, bg.questionCount(), random).forEach((subject, n) -> {
                if (n > 0) {
                    section.addAll(QuestionPicker.pick(idsBySubject.get(subject), n, random));
                }
            });
            // sinflar savollari bo'lim ichida aralash
            Collections.shuffle(section, random);
            picked.addAll(section);
            session.getGroups().add(new SessionGroup(group, groupOrder++, bg.questionCount(), group.getScoreWeight()));
            for (Subject subject : idsBySubject.keySet()) {
                session.getSubjects().add(new SessionSubject(subject, group, subjectOrder++));
            }
        }
        session.setTimeLimitSeconds(session.getGroups().size() * BLOCK_SECONDS_PER_SUBJECT);
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
        if (old.isBlock()) {
            // Guruhlar tartibi va ballari eski testdagidek qoladi, savollar soni – faqat xatolar
            Map<Long, Long> wrongBySubject = wrong.stream()
                    .collect(Collectors.groupingBy(sq -> subjectOf(sq).getId(), Collectors.counting()));
            Map<Long, Long> wrongByGroup = wrong.stream()
                    .collect(Collectors.groupingBy(sq -> groupOf(old, sq).getId(), Collectors.counting()));
            int order = 0;
            for (SessionGroup sg : old.getGroups()) {
                Long n = wrongByGroup.get(sg.getGroup().getId());
                if (n != null) {
                    session.getGroups().add(new SessionGroup(sg.getGroup(), order++, n.intValue(), sg.getScoreWeight()));
                }
            }
            order = 0;
            for (SessionSubject ss : old.getSubjects()) {
                if (wrongBySubject.containsKey(ss.getSubject().getId())) {
                    session.getSubjects().add(new SessionSubject(ss.getSubject(), ss.getGroup(), order++));
                }
            }
            session.setTimeLimitSeconds(session.getGroups().size() * BLOCK_SECONDS_PER_SUBJECT);
            // savollar fanlar bo'limlari tartibida, bo'lim ichida aralash
            Map<Long, Integer> groupOrder = new HashMap<>();
            session.getGroups().forEach(sg -> groupOrder.put(sg.getGroup().getId(), sg.getOrderIndex()));
            Map<Long, Integer> questionGroupOrder = new HashMap<>();
            wrong.forEach(sq -> questionGroupOrder.put(sq.getQuestion().getId(), groupOrder.get(groupOf(old, sq).getId())));
            ids.sort(Comparator.comparing(questionGroupOrder::get));
        } else {
            wrong.stream().map(sq -> sq.getQuestion().getTopic()).distinct().forEach(session.getTopics()::add);
        }
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

    /** [topicId -> faol savollar id lari] */
    private Map<Long, List<Long>> activeQuestionIdsByTopic(Collection<Long> topicIds) {
        Map<Long, List<Long>> idsByTopic = new LinkedHashMap<>();
        if (topicIds.isEmpty()) {
            return idsByTopic;
        }
        for (Object[] row : questionRepository.findActiveIdsByTopicIds(topicIds)) {
            idsByTopic.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        return idsByTopic;
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

    @Transactional
    public SessionView get(Long userId, Long sessionId) {
        TestSession s = find(userId, sessionId);
        Instant now = Instant.now();
        if (s.isExpired(now)) {
            finishSession(s, now);
        }
        Long remaining = s.deadline() == null ? null
                : s.isFinished() ? 0L : Math.max(0, Duration.between(now, s.deadline()).toSeconds());
        List<QuestionView> questions = s.getQuestions().stream()
                .map(sq -> toView(s, sq, canReveal(s, sq)))
                .toList();
        return new SessionView(s.getId(), subjectId(s), subjectName(s), s.getMode(),
                s.getStatus(), s.getQuestionCount(), s.getStartedAt(), s.getFinishedAt(), s.getCorrectCount(),
                s.getScorePercent(), s.getTimeLimitSeconds(), remaining, topicRefs(s), groupViews(s), questions);
    }

    /** Vaqt tugagan bo'lsa test yakunlanadi va xato qaytariladi – yakunlash bekor qilinmasligi uchun noRollbackFor. */
    @Transactional(noRollbackFor = ApiException.class)
    public AnswerResponse answer(Long userId, Long sessionId, AnswerRequest req) {
        TestSession s = find(userId, sessionId);
        Instant now = Instant.now();
        if (s.isExpired(now)) {
            finishSession(s, now);
            throw ApiException.badRequest("Vaqt tugadi, test yakunlandi");
        }
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
            finishSession(s, Instant.now());
        }
        return buildResult(s);
    }

    /** Vaqti tugaganda yakunlash vaqti – muddat (keyinroq ochilsa ham sarflangan vaqt oshib ketmaydi). */
    private void finishSession(TestSession s, Instant now) {
        int correct = (int) s.getQuestions().stream().filter(sq -> Boolean.TRUE.equals(sq.getCorrect())).count();
        s.setCorrectCount(correct);
        if (s.isBlock()) {
            List<GroupResult> results = groupResults(s);
            double score = round1(results.stream().mapToDouble(GroupResult::score).sum());
            double max = round1(results.stream().mapToDouble(GroupResult::maxScore).sum());
            s.setScore(score);
            s.setMaxScore(max);
            s.setScorePercent(percent(score, max));
        } else {
            s.setScorePercent(percent(correct, s.getQuestionCount()));
        }
        s.setStatus(TestStatus.FINISHED);
        Instant deadline = s.deadline();
        s.setFinishedAt(deadline != null && now.isAfter(deadline) ? deadline : now);
    }

    /** Foydalanuvchining vaqti tugagan testlarini yakunlaydi (tarix va statistika to'g'ri bo'lishi uchun). */
    @Transactional
    public void finishExpired(Long userId) {
        Instant now = Instant.now();
        sessionRepository.findExpired(userId).forEach(s -> finishSession(s, now));
    }

    @Transactional
    public ResultView result(Long userId, Long sessionId) {
        TestSession s = find(userId, sessionId);
        Instant now = Instant.now();
        if (s.isExpired(now)) {
            finishSession(s, now);
        }
        if (!s.isFinished()) {
            throw ApiException.badRequest("Test hali yakunlanmagan");
        }
        return buildResult(s);
    }

    @Transactional
    public PageResponse<SessionSummary> history(Long userId, int page, int size) {
        finishExpired(userId);
        var sessions = sessionRepository.findAllByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = sessions.getContent().stream().map(TestSession::getId).toList();
        Map<Long, Long> answered = ids.isEmpty() ? Map.of() : Counts.toMap(sessionRepository.countAnswered(ids));
        return PageResponse.of(sessions, s -> new SessionSummary(s.getId(), subjectId(s), subjectName(s),
                s.getMode(), s.getStatus(), s.getQuestionCount(), answered.getOrDefault(s.getId(), 0L),
                s.getCorrectCount(), s.getScorePercent(), s.getScore(), s.getMaxScore(),
                s.getStartedAt(), s.getFinishedAt(), s.getTopics().stream().map(Topic::getName).toList(),
                s.getGroups().stream().map(sg -> groupLabel(s, sg)).toList()));
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
        double scorePercent = s.getScorePercent() != null ? s.getScorePercent() : percent(correct, s.getQuestionCount());
        return new ResultView(s.getId(), subjectId(s), subjectName(s), s.getMode(),
                s.getQuestionCount(), correct, wrong, unanswered, scorePercent, s.getScore(), s.getMaxScore(),
                s.getStartedAt(), s.getFinishedAt(), duration, s.getTimeLimitSeconds(), s.isBlock() ? groupResults(s) : List.of(),
                topicResults, qs.stream().map(sq -> toView(s, sq, true)).toList());
    }

    /** Blok test: fan guruhlari bo'yicha natija va ball (guruhlar test yaratilgandagi tartibda). */
    private List<GroupResult> groupResults(TestSession s) {
        Map<Long, List<SessionQuestion>> byGroup = s.getQuestions().stream()
                .collect(Collectors.groupingBy(sq -> groupOf(s, sq).getId()));
        return s.getGroups().stream()
                .map(sg -> {
                    List<SessionQuestion> list = byGroup.getOrDefault(sg.getGroup().getId(), List.of());
                    int total = list.size();
                    int c = (int) list.stream().filter(sq -> Boolean.TRUE.equals(sq.getCorrect())).count();
                    int u = (int) list.stream().filter(sq -> !sq.isAnswered()).count();
                    double w = sg.getScoreWeight();
                    return new GroupResult(sg.getGroup().getId(), sg.getGroup().getName(), subjectNames(s, sg), w,
                            total, c, total - c - u, u, percent(c, total), round1(c * w), round1(total * w));
                })
                .toList();
    }

    /** Savol qaysi guruh hisobiga kiradi – test yaratilgandagi guruh bo'yicha. */
    private static SubjectGroup groupOf(TestSession s, SessionQuestion sq) {
        Subject subject = subjectOf(sq);
        return s.getSubjects().stream()
                .filter(ss -> ss.getSubject().getId().equals(subject.getId()))
                .map(SessionSubject::getGroup)
                .findFirst()
                .orElse(subject.getGroup());
    }

    private static List<String> subjectNames(TestSession s, SessionGroup sg) {
        return s.getSubjects().stream()
                .filter(ss -> ss.getGroup().getId().equals(sg.getGroup().getId()))
                .map(ss -> ss.getSubject().getName())
                .toList();
    }

    /** "Matematika (1-sinf, 2-sinf)"; guruhda fan o'zi bitta va nomi bir xil bo'lsa – faqat guruh nomi. */
    private static String groupLabel(TestSession s, SessionGroup sg) {
        String group = sg.getGroup().getName();
        List<String> parts = subjectNames(s, sg).stream()
                .map(name -> {
                    String shortName = name.replaceAll("(?i)" + Pattern.quote(group), "").trim();
                    return shortName.isEmpty() ? name : shortName;
                })
                .filter(part -> !part.equalsIgnoreCase(group))
                .toList();
        return parts.isEmpty() ? group : group + " (" + String.join(", ", parts) + ")";
    }

    private static Subject subjectOf(SessionQuestion sq) {
        return sq.getQuestion().getTopic().getSubject();
    }

    private static Long subjectId(TestSession s) {
        return s.getSubject() != null ? s.getSubject().getId() : null;
    }

    /** Blok testda – guruhlar vergul bilan: "Matematika (1-sinf, 2-sinf), Biologiya (5-sinf)". */
    private static String subjectName(TestSession s) {
        if (s.getSubject() != null) {
            return s.getSubject().getName();
        }
        return s.getGroups().stream().map(sg -> groupLabel(s, sg)).collect(Collectors.joining(", "));
    }

    private static List<SessionGroupView> groupViews(TestSession s) {
        return s.getGroups().stream()
                .map(sg -> new SessionGroupView(sg.getGroup().getId(), sg.getGroup().getName(), subjectNames(s, sg),
                        sg.getQuestionCount(), sg.getScoreWeight()))
                .toList();
    }

    /** O'rganish rejimida javob berilgan savol, yoki yakunlangan test – to'g'ri javob ko'rsatilishi mumkin. */
    private boolean canReveal(TestSession s, SessionQuestion sq) {
        return s.isFinished() || (s.getMode() == TestMode.LEARNING && sq.isAnswered());
    }

    private QuestionView toView(TestSession s, SessionQuestion sq, boolean reveal) {
        Question q = sq.getQuestion();
        Map<Long, AnswerOption> optionsById = q.getOptions().stream()
                .collect(Collectors.toMap(AnswerOption::getId, Function.identity()));
        List<OptionView> options = sq.optionIds().stream()
                .map(optionsById::get)
                .filter(Objects::nonNull)
                .map(o -> new OptionView(o.getId(), o.getText()))
                .toList();
        Subject subject = q.getTopic().getSubject();
        // Blok testda savol qaysi fan bo'limiga tegishli (masalan "1-sinf Matematika" -> Matematika)
        SubjectGroup group = s.isBlock() ? groupOf(s, sq) : null;
        return new QuestionView(sq.getId(), sq.getOrderIndex(), subject.getId(), subject.getName(),
                group != null ? group.getId() : null, group != null ? group.getName() : null,
                q.getTopic().getId(), q.getTopic().getName(),
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

    static double percent(double part, double total) {
        if (total == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
