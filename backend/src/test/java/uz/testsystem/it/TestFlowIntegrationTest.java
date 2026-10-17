package uz.testsystem.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.seed.location=classpath:demo/*.json")
@AutoConfigureMockMvc
@Testcontainers
class TestFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    String token;

    @BeforeEach
    void registerUser() throws Exception {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode res = call(post("/api/auth/register"),
                Map.of("username", username, "password", "secret123", "fullName", "Test User"), 200);
        token = res.get("token").asText();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/subjects")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessAdminApi() throws Exception {
        call(get("/api/admin/subjects"), null, 403);
    }

    @Test
    void learningModeFlow() throws Exception {
        JsonNode subject = firstSubject();
        List<Long> topicIds = topicIds(subject.get("id").asLong());

        long sessionId = call(post("/api/tests"), Map.of(
                "subjectId", subject.get("id").asLong(),
                "topicIds", topicIds,
                "questionCount", 6,
                "mode", "LEARNING"), 201).get("id").asLong();

        JsonNode session = call(get("/api/tests/" + sessionId), null, 200);
        JsonNode questions = session.get("questions");
        assertThat(questions).hasSize(6);
        // to'g'ri javob oldindan yuborilmaydi
        for (JsonNode q : questions) {
            assertThat(q.get("correctOptionId").isNull()).isTrue();
            assertThat(q.get("explanation").isNull()).isTrue();
        }
        // savollar mavzular bo'yicha teng taqsimlangan (3 mavzu × 2)
        Map<Long, Integer> perTopic = new HashMap<>();
        questions.forEach(q -> perTopic.merge(q.get("topicId").asLong(), 1, Integer::sum));
        assertThat(perTopic.values()).containsOnly(2);

        // birinchi savolga javob – o'rganish rejimida natija darhol qaytadi
        JsonNode q0 = questions.get(0);
        long optionId = q0.get("options").get(0).get("id").asLong();
        JsonNode answer = call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0.get("id").asLong(), "optionId", optionId), 200);
        assertThat(answer.get("correct").isBoolean()).isTrue();
        assertThat(answer.get("correctOptionId").asLong()).isPositive();
        assertThat(answer.get("explanation").asText()).isNotBlank();

        // qayta javob berib bo'lmaydi
        call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0.get("id").asLong(), "optionId", optionId), 409);

        JsonNode result = call(post("/api/tests/" + sessionId + "/finish"), null, 200);
        int correct = answer.get("correct").asBoolean() ? 1 : 0;
        assertThat(result.get("correctCount").asInt()).isEqualTo(correct);
        assertThat(result.get("unansweredCount").asInt()).isEqualTo(5);
        assertThat(result.get("questions")).hasSize(6);
        result.get("questions").forEach(q -> assertThat(q.get("correctOptionId").asLong()).isPositive());

        // xatolarni qayta yechish: xato + javobsiz savollar
        long retryId = call(post("/api/tests/" + sessionId + "/retry-wrong"), null, 201).get("id").asLong();
        JsonNode retry = call(get("/api/tests/" + retryId), null, 200);
        assertThat(retry.get("questionCount").asInt()).isEqualTo(6 - correct);

        // tarix va statistika
        JsonNode history = call(get("/api/tests/history"), null, 200);
        assertThat(history.get("total").asLong()).isEqualTo(2);
        JsonNode stats = call(get("/api/stats/me"), null, 200);
        assertThat(stats.get("totalAnswered").asLong()).isEqualTo(1);
    }

    @Test
    void examModeHidesAnswersUntilFinished() throws Exception {
        JsonNode subject = firstSubject();
        List<Long> topicIds = topicIds(subject.get("id").asLong());

        long sessionId = call(post("/api/tests"), Map.of(
                "subjectId", subject.get("id").asLong(),
                "topicIds", topicIds.subList(0, 1),
                "questionCount", 3,
                "mode", "EXAM"), 201).get("id").asLong();
        JsonNode q0 = call(get("/api/tests/" + sessionId), null, 200).get("questions").get(0);

        JsonNode options = q0.get("options");
        long first = options.get(0).get("id").asLong();
        long second = options.get(1).get("id").asLong();
        JsonNode answer = call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0.get("id").asLong(), "optionId", first), 200);
        assertThat(answer.get("correct").isNull()).isTrue();
        assertThat(answer.get("correctOptionId").isNull()).isTrue();

        // imtihon rejimida javobni o'zgartirish mumkin
        call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0.get("id").asLong(), "optionId", second), 200);

        call(get("/api/tests/" + sessionId + "/result"), null, 400);
        JsonNode result = call(post("/api/tests/" + sessionId + "/finish"), null, 200);
        assertThat(result.get("questions").get(0).get("selectedOptionId").asLong()).isEqualTo(second);

        // yakunlangan testga javob berib bo'lmaydi
        call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0.get("id").asLong(), "optionId", first), 400);
    }

    @Test
    void rejectsTooManyQuestionsAndForeignTopics() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        long s1 = subjects.get(0).get("id").asLong();
        long s2 = subjects.get(1).get("id").asLong();
        List<Long> topics1 = topicIds(s1);
        List<Long> topics2 = topicIds(s2);

        call(post("/api/tests"), Map.of("subjectId", s1, "topicIds", topics1.subList(0, 1),
                "questionCount", 11), 400);
        call(post("/api/tests"), Map.of("subjectId", s1, "topicIds", topics2,
                "questionCount", 1), 400);
        call(post("/api/tests"), Map.of("subjectId", s1, "topicIds", List.of(),
                "questionCount", 1), 400);
    }

    @Test
    void cannotSeeOtherUsersTest() throws Exception {
        JsonNode subject = firstSubject();
        long sessionId = call(post("/api/tests"), Map.of(
                "subjectId", subject.get("id").asLong(),
                "topicIds", topicIds(subject.get("id").asLong()),
                "questionCount", 1), 201).get("id").asLong();

        registerUser();
        call(get("/api/tests/" + sessionId), null, 404);
    }

    @Test
    void blockExamKeepsSubjectsInSectionsAndScoresByWeight() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        Map<Long, Double> weights = new HashMap<>();
        subjects.forEach(s -> weights.put(s.get("groupId").asLong(), s.get("scoreWeight").asDouble()));
        assertThat(weights.values()).contains(1.1, 2.1);
        JsonNode java = subjects.get(0);
        JsonNode math = subjects.get(1);
        long g1 = java.get("groupId").asLong();
        long g2 = math.get("groupId").asLong();

        long sessionId = call(post("/api/tests/block"), Map.of("groups", List.of(
                block(g1, List.of(java.get("id").asLong()), 4),
                block(g2, List.of(math.get("id").asLong()), 3))), 201).get("id").asLong();

        JsonNode session = call(get("/api/tests/" + sessionId), null, 200);
        assertThat(session.get("mode").asText()).isEqualTo("BLOCK");
        assertThat(session.get("subjectId").isNull()).isTrue();
        assertThat(session.get("groups")).hasSize(2);
        assertThat(session.get("questions")).hasSize(7);
        Map<Long, Integer> perSubject = new HashMap<>();
        session.get("questions").forEach(q -> {
            perSubject.merge(q.get("subjectId").asLong(), 1, Integer::sum);
            assertThat(q.get("correctOptionId").isNull()).isTrue();
        });
        assertThat(perSubject).containsEntry(java.get("id").asLong(), 4).containsEntry(math.get("id").asLong(), 3);
        // fanlar alohida bo'limlarda: avval 4 ta Java, keyin 3 ta Matematika
        assertThat(groupSequence(session)).containsExactly(g1, g1, g1, g1, g2, g2, g2);
        assertThat(session.get("questions").get(0).get("groupName").asText()).isEqualTo(java.get("groupName").asText());

        // imtihondagidek: javob yakunlaguncha ochilmaydi va o'zgartirilishi mumkin
        JsonNode q0 = session.get("questions").get(0);
        JsonNode answer = call(post("/api/tests/" + sessionId + "/answers"), Map.of(
                "sessionQuestionId", q0.get("id").asLong(),
                "optionId", q0.get("options").get(0).get("id").asLong()), 200);
        assertThat(answer.get("correct").isNull()).isTrue();
        for (JsonNode q : session.get("questions")) {
            call(post("/api/tests/" + sessionId + "/answers"), Map.of(
                    "sessionQuestionId", q.get("id").asLong(),
                    "optionId", q.get("options").get(1).get("id").asLong()), 200);
        }

        JsonNode result = call(post("/api/tests/" + sessionId + "/finish"), null, 200);
        assertThat(result.get("byGroup")).hasSize(2);
        double expected = 0;
        for (JsonNode gr : result.get("byGroup")) {
            double w = weights.get(gr.get("groupId").asLong());
            assertThat(gr.get("scoreWeight").asDouble()).isEqualTo(w);
            assertThat(gr.get("score").asDouble()).isEqualTo(Math.round(gr.get("correct").asInt() * w * 10) / 10.0);
            expected += gr.get("correct").asInt() * w;
        }
        assertThat(result.get("score").asDouble()).isEqualTo(Math.round(expected * 10) / 10.0);
        double max = Math.round((4 * weights.get(g1) + 3 * weights.get(g2)) * 10) / 10.0;
        assertThat(result.get("maxScore").asDouble()).isEqualTo(max);

        JsonNode item = call(get("/api/tests/history"), null, 200).get("items").get(0);
        assertThat(item.get("mode").asText()).isEqualTo("BLOCK");
        assertThat(item.get("maxScore").asDouble()).isEqualTo(max);
        assertThat(item.get("groupLabels")).hasSize(2);
        assertThat(call(get("/api/stats/me"), null, 200).get("bySubject")).hasSize(2);

        if (result.get("correctCount").asInt() < 7) {
            long retryId = call(post("/api/tests/" + sessionId + "/retry-wrong"), null, 201).get("id").asLong();
            JsonNode retry = call(get("/api/tests/" + retryId), null, 200);
            assertThat(retry.get("mode").asText()).isEqualTo("BLOCK");
            assertThat(retry.get("questionCount").asInt()).isEqualTo(7 - result.get("correctCount").asInt());
            List<Long> seq = groupSequence(retry);
            List<Long> sorted = new ArrayList<>(seq);
            sorted.sort(Comparator.comparing(g -> g == g1 ? 0 : 1));
            assertThat(seq).isEqualTo(sorted);
        }
    }

    @Test
    void blockGroupCombinesGradesWithCommonCountAndWeight() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        List<JsonNode> math = new ArrayList<>();
        subjects.forEach(s -> {
            if (s.get("groupName").asText().equals("Matematika")) {
                math.add(s);
            }
        });
        assertThat(math).hasSize(2);
        long groupId = math.get(0).get("groupId").asLong();
        assertThat(math.get(1).get("groupId").asLong()).isEqualTo(groupId);
        assertThat(math.get(1).get("scoreWeight").asDouble()).isEqualTo(1.1);
        List<Long> ids = math.stream().map(s -> s.get("id").asLong()).toList();

        long sessionId = call(post("/api/tests/block"), Map.of("groups", List.of(block(groupId, ids, 10))), 201)
                .get("id").asLong();
        JsonNode session = call(get("/api/tests/" + sessionId), null, 200);
        assertThat(session.get("timeLimitSeconds").asInt()).isEqualTo(3600);
        assertThat(session.get("groups")).hasSize(1);
        assertThat(session.get("groups").get(0).get("subjectNames")).hasSize(2);
        // sinflar orasida teng: 5 + 5
        Map<Long, Integer> perSubject = new HashMap<>();
        session.get("questions").forEach(q -> perSubject.merge(q.get("subjectId").asLong(), 1, Integer::sum));
        assertThat(perSubject).containsEntry(ids.get(0), 5).containsEntry(ids.get(1), 5);

        JsonNode result = call(post("/api/tests/" + sessionId + "/finish"), null, 200);
        assertThat(result.get("byGroup")).hasSize(1);
        assertThat(result.get("byGroup").get(0).get("total").asInt()).isEqualTo(10);
        assertThat(result.get("maxScore").asDouble()).isEqualTo(11.0);
        assertThat(result.get("subjectName").asText()).isEqualTo("Matematika (qo'shimcha)");

        // faqat bitta sinf ham tanlanishi mumkin
        call(post("/api/tests/block"), Map.of("groups", List.of(block(groupId, List.of(ids.get(1)), 10))), 201);
        call(post("/api/tests/block"), Map.of("groups", List.of(block(groupId, List.of(ids.get(1)), 11))), 400);
    }

    @Test
    void blockExamHasOneHourPerSubjectAndFinishesWhenTimeIsUp() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        JsonNode s1 = subjects.get(0);
        JsonNode s2 = subjects.get(1);
        long sessionId = call(post("/api/tests/block"), Map.of("groups", List.of(
                block(s1.get("groupId").asLong(), List.of(s1.get("id").asLong()), 2),
                block(s2.get("groupId").asLong(), List.of(s2.get("id").asLong()), 2))), 201).get("id").asLong();

        JsonNode session = call(get("/api/tests/" + sessionId), null, 200);
        assertThat(session.get("timeLimitSeconds").asInt()).isEqualTo(2 * 3600);
        assertThat(session.get("remainingSeconds").asLong()).isBetween(2 * 3600L - 60, 2 * 3600L);
        JsonNode q0 = session.get("questions").get(0);
        call(post("/api/tests/" + sessionId + "/answers"), Map.of(
                "sessionQuestionId", q0.get("id").asLong(),
                "optionId", q0.get("options").get(0).get("id").asLong()), 200);

        // vaqt tugadi: javob qabul qilinmaydi, test yakunlanadi, sarflangan vaqt = 2 soat
        jdbc.update("update test_sessions set started_at = started_at - interval '3 hours' where id = ?", sessionId);
        JsonNode q1 = session.get("questions").get(1);
        call(post("/api/tests/" + sessionId + "/answers"), Map.of(
                "sessionQuestionId", q1.get("id").asLong(),
                "optionId", q1.get("options").get(0).get("id").asLong()), 400);
        JsonNode result = call(get("/api/tests/" + sessionId + "/result"), null, 200);
        assertThat(result.get("durationSeconds").asLong()).isEqualTo(2 * 3600L);
        assertThat(result.get("unansweredCount").asInt()).isEqualTo(3);
        assertThat(result.get("score").isNull()).isFalse();

        // tarix ochilganda ham vaqti o'tgan test yakunlanadi
        long second = call(post("/api/tests/block"), Map.of("groups", List.of(
                block(s1.get("groupId").asLong(), List.of(s1.get("id").asLong()), 1))), 201).get("id").asLong();
        jdbc.update("update test_sessions set started_at = started_at - interval '2 hours' where id = ?", second);
        JsonNode item = call(get("/api/tests/history"), null, 200).get("items").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(second);
        assertThat(item.get("status").asText()).isEqualTo("FINISHED");

        // oddiy testda vaqt chegarasi yo'q
        long plain = call(post("/api/tests"), Map.of("subjectId", s1.get("id").asLong(),
                "topicIds", topicIds(s1.get("id").asLong()), "questionCount", 1), 201).get("id").asLong();
        assertThat(call(get("/api/tests/" + plain), null, 200).get("timeLimitSeconds").isNull()).isTrue();
    }

    @Test
    void blockExamValidation() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        JsonNode java = subjects.get(0);
        long s1 = java.get("id").asLong();
        long g1 = java.get("groupId").asLong();
        long g2 = subjects.get(1).get("groupId").asLong();
        long available = java.get("questionCount").asLong();
        call(post("/api/tests/block"), Map.of("groups", List.of()), 400);
        call(post("/api/tests/block"), Map.of("groups", List.of(block(g1, List.of(), 1))), 400);
        call(post("/api/tests/block"), Map.of("groups", List.of(
                block(g1, List.of(s1), 1), block(g1, List.of(s1), 1))), 400);
        call(post("/api/tests/block"), Map.of("groups", List.of(block(g1, List.of(s1), available + 1))), 400);
        // fan boshqa guruhga tegishli
        call(post("/api/tests/block"), Map.of("groups", List.of(block(g2, List.of(s1), 1))), 400);
        call(post("/api/tests/block"), Map.of("groups", List.of(block(999999, List.of(s1), 1))), 404);
        call(post("/api/tests"), Map.of("subjectId", s1, "topicIds", topicIds(s1), "questionCount", 1,
                "mode", "BLOCK"), 400);
    }

    private static List<Long> groupSequence(JsonNode session) {
        List<Long> seq = new ArrayList<>();
        session.get("questions").forEach(q -> seq.add(q.get("groupId").asLong()));
        return seq;
    }

    private static Map<String, Object> block(long groupId, List<Long> subjectIds, long questionCount) {
        return Map.of("groupId", groupId, "subjectIds", subjectIds, "questionCount", questionCount);
    }

    @Test
    void clearHistoryRemovesOnlyOwnSessionsAndKeepsQuestions() throws Exception {
        JsonNode subject = firstSubject();
        long subjectId = subject.get("id").asLong();
        Map<String, Object> req = Map.of("subjectId", subjectId, "topicIds", topicIds(subjectId), "questionCount", 2);
        long otherSessionId = call(post("/api/tests"), req, 201).get("id").asLong();
        String otherToken = token;

        registerUser();
        long sessionId = call(post("/api/tests"), req, 201).get("id").asLong();
        long questionCount = call(get("/api/subjects"), null, 200).get(0).get("questionCount").asLong();

        call(delete("/api/tests/history"), null, 204);

        assertThat(call(get("/api/tests/history"), null, 200).get("total").asLong()).isZero();
        assertThat(call(get("/api/stats/me"), null, 200).get("totalTests").asLong()).isZero();
        call(get("/api/tests/" + sessionId), null, 404);
        assertThat(call(get("/api/subjects"), null, 200).get(0).get("questionCount").asLong()).isEqualTo(questionCount);

        token = otherToken;
        call(get("/api/tests/" + otherSessionId), null, 200);
    }

    @Test
    void flashcardsRevealAnswerAndAreSelfGraded() throws Exception {
        JsonNode subject = firstSubject();
        long subjectId = subject.get("id").asLong();
        long sessionId = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", topicIds(subjectId),
                "questionCount", 3, "mode", "FLASHCARD"), 201).get("id").asLong();
        JsonNode questions = call(get("/api/tests/" + sessionId), null, 200).get("questions");
        // kartochkada javob va izoh oldindan ochiq, baho esa yo'q
        for (JsonNode q : questions) {
            assertThat(q.get("correctOptionId").asLong()).isPositive();
            assertThat(q.get("explanation").asText()).isNotBlank();
            assertThat(q.get("correct").isNull()).isTrue();
        }
        long q0 = questions.get(0).get("id").asLong();
        long q1 = questions.get(1).get("id").asLong();
        call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0, "optionId", questions.get(0).get("options").get(0).get("id").asLong()), 400);
        assertThat(call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q0, "known", true), 200).get("correct").asBoolean()).isTrue();
        assertThat(call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q1, "known", false), 200).get("correct").asBoolean()).isFalse();
        call(post("/api/tests/" + sessionId + "/answers"), Map.of("sessionQuestionId", q1, "known", true), 409);

        JsonNode result = call(post("/api/tests/" + sessionId + "/finish"), null, 200);
        assertThat(result.get("correctCount").asInt()).isEqualTo(1);
        assertThat(result.get("wrongCount").asInt()).isEqualTo(1);
        // o'zi baholagan javoblar aniqlik statistikasiga kirmaydi, lekin progressga kiradi
        assertThat(call(get("/api/stats/me"), null, 200).get("totalAnswered").asLong()).isZero();
        JsonNode overview = call(get("/api/progress/overview?tz=Asia/Tashkent"), null, 200);
        assertThat(overview.get("totals").get("flashcards").asLong()).isEqualTo(2);
        assertThat(overview.get("xp").asLong()).isEqualTo(2);
        assertThat(result.get("xp").asInt()).isEqualTo(2);
        assertThat(overview.get("mistakesCount").asInt()).isEqualTo(1);
    }

    @Test
    void blitzHasOneMinuteAndKeepsOnlyAnsweredQuestions() throws Exception {
        JsonNode subject = firstSubject();
        long subjectId = subject.get("id").asLong();
        long sessionId = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", topicIds(subjectId),
                "questionCount", 10, "mode", "BLITZ"), 201).get("id").asLong();
        JsonNode session = call(get("/api/tests/" + sessionId), null, 200);
        assertThat(session.get("timeLimitSeconds").asInt()).isEqualTo(60);
        int wrong = 0;
        for (int i = 0; i < 2; i++) {
            JsonNode q = session.get("questions").get(i);
            JsonNode answer = call(post("/api/tests/" + sessionId + "/answers"), Map.of(
                    "sessionQuestionId", q.get("id").asLong(),
                    "optionId", q.get("options").get(0).get("id").asLong()), 200);
            // blitsda javob darhol ochiladi
            assertThat(answer.get("correctOptionId").asLong()).isPositive();
            wrong += answer.get("correct").asBoolean() ? 0 : 1;
        }
        jdbc.update("update test_sessions set started_at = started_at - interval '2 minutes' where id = ?", sessionId);
        JsonNode result = call(get("/api/tests/" + sessionId + "/result"), null, 200);
        assertThat(result.get("questionCount").asInt()).isEqualTo(2);
        assertThat(result.get("unansweredCount").asInt()).isZero();
        assertThat(result.get("questions")).hasSize(2);
        assertThat(result.get("durationSeconds").asLong()).isEqualTo(60);
        if (wrong > 0) {
            long retryId = call(post("/api/tests/" + sessionId + "/retry-wrong"), null, 201).get("id").asLong();
            JsonNode retry = call(get("/api/tests/" + retryId), null, 200);
            assertThat(retry.get("mode").asText()).isEqualTo("LEARNING");
            assertThat(retry.get("timeLimitSeconds").isNull()).isTrue();
            assertThat(retry.get("questionCount").asInt()).isEqualTo(wrong);
        }
    }

    @Test
    void spacedRepetitionReviewAndMistakesNotebook() throws Exception {
        JsonNode subject = firstSubject();
        long subjectId = subject.get("id").asLong();
        long topicId = topicIds(subjectId).getFirst();
        long sessionId = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", List.of(topicId),
                "questionCount", 3, "mode", "LEARNING"), 201).get("id").asLong();
        JsonNode questions = call(get("/api/tests/" + sessionId), null, 200).get("questions");
        answer(sessionId, questions.get(0), true);
        answer(sessionId, questions.get(1), false);
        answer(sessionId, questions.get(2), false);

        // xato javoblar darhol takrorlashga chiqadi, to'g'ri javob – 1 kundan keyin
        JsonNode overview = call(get("/api/progress/overview?tz=Asia/Tashkent"), null, 200);
        assertThat(overview.get("dueCount").asInt()).isEqualTo(2);
        assertThat(overview.get("mistakesCount").asInt()).isEqualTo(2);
        assertThat(overview.get("xp").asLong()).isEqualTo(1);
        assertThat(overview.get("days")).hasSize(1);
        assertThat(overview.get("days").get(0).get("answered").asLong()).isEqualTo(3);

        JsonNode progress = call(get("/api/progress/subjects/" + subjectId), null, 200);
        JsonNode topic = progress.get("topics").get(0);
        assertThat(topic.get("topicId").asLong()).isEqualTo(topicId);
        assertThat(topic.get("seen").asInt()).isEqualTo(3);
        assertThat(topic.get("mistakes").asInt()).isEqualTo(2);
        assertThat(topic.get("level").asText()).isEqualTo("LEARNING");
        assertThat(progress.get("topics").get(1).get("level").asText()).isEqualTo("NEW");

        JsonNode mistakes = call(get("/api/progress/mistakes?subjectId=" + subjectId), null, 200);
        assertThat(mistakes).hasSize(2);
        mistakes.forEach(m -> {
            assertThat(m.get("correctAnswer").asText()).isNotBlank();
            assertThat(m.get("yourAnswer").asText()).isNotBlank();
            assertThat(m.get("yourAnswer").asText()).isNotEqualTo(m.get("correctAnswer").asText());
        });

        // davom etayotgan imtihondagi javoblar progressga kirmaydi
        long exam = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", List.of(topicId),
                "questionCount", 10, "mode", "EXAM"), 201).get("id").asLong();
        JsonNode examQs = call(get("/api/tests/" + exam), null, 200).get("questions");
        for (JsonNode q : examQs) {
            answer(exam, q, false);
        }
        assertThat(call(get("/api/progress/overview"), null, 200).get("mistakesCount").asInt()).isEqualTo(2);
        call(delete("/api/tests/history"), null, 204);
        assertThat(call(get("/api/progress/overview"), null, 200).get("xp").asLong()).isZero();

        // tarix tozalangach qaytadan: takrorlash testi faqat vaqti kelgan savollardan
        sessionId = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", List.of(topicId),
                "questionCount", 3, "mode", "LEARNING"), 201).get("id").asLong();
        questions = call(get("/api/tests/" + sessionId), null, 200).get("questions");
        answer(sessionId, questions.get(0), true);
        answer(sessionId, questions.get(1), false);
        call(post("/api/tests/review"), Map.of("subjectId", subjectId, "kind", "DUE", "questionCount", 10), 201);
        long review = call(post("/api/tests/review"), Map.of("subjectId", subjectId, "kind", "MISTAKES",
                "questionCount", 10), 201).get("id").asLong();
        JsonNode reviewQs = call(get("/api/tests/" + review), null, 200).get("questions");
        assertThat(reviewQs).hasSize(1);
        assertThat(reviewQs.get(0).get("text").asText()).isEqualTo(questions.get(1).get("text").asText());
        answer(review, reviewQs.get(0), true);
        assertThat(call(get("/api/progress/overview"), null, 200).get("dueCount").asInt()).isZero();
        assertThat(call(post("/api/tests/" + review + "/finish"), null, 200).get("xp").asInt()).isEqualTo(1);
        call(post("/api/tests/review"), Map.of("subjectId", subjectId, "kind", "MISTAKES", "questionCount", 10), 400);

        // 1 kundan keyin to'g'ri javob berilgan savollar ham takrorlashga chiqadi
        jdbc.update("""
                update session_questions set answered_at = answered_at - interval '2 days'
                where session_id in (select id from test_sessions where user_id =
                    (select user_id from test_sessions where id = ?))""", review);
        assertThat(call(get("/api/progress/overview"), null, 200).get("dueCount").asInt()).isEqualTo(2);

        JsonNode board = call(get("/api/progress/leaderboard"), null, 200);
        assertThat(board.get("me").get("xp").asLong()).isPositive();
        assertThat(board.get("me").get("rank").asInt()).isPositive();

        // 24 soat ichida o'sha savollarni qayta to'g'ri yechish XP bermaydi. Mavzudagi 10 savoldan 2 tasi XP olgan,
        // lekin javoblar 2 kun oldinga surildi – endi hammasi yana XP beradi: 10 + (5 + 10) bonus
        long full = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", List.of(topicId),
                "questionCount", 10, "mode", "EXAM"), 201).get("id").asLong();
        for (JsonNode q : call(get("/api/tests/" + full), null, 200).get("questions")) {
            answer(full, q, true);
        }
        assertThat(call(post("/api/tests/" + full + "/finish"), null, 200).get("xp").asInt()).isEqualTo(10 + 15);
        // darhol yana – endi faqat bonus
        long repeat = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", List.of(topicId),
                "questionCount", 10, "mode", "EXAM"), 201).get("id").asLong();
        for (JsonNode q : call(get("/api/tests/" + repeat), null, 200).get("questions")) {
            answer(repeat, q, true);
        }
        assertThat(call(post("/api/tests/" + repeat + "/finish"), null, 200).get("xp").asInt()).isEqualTo(15);
    }

    @Test
    void learningStartsWithEasyQuestions() throws Exception {
        JsonNode subject = firstSubject();
        long subjectId = subject.get("id").asLong();
        long sessionId = call(post("/api/tests"), Map.of("subjectId", subjectId, "topicIds", topicIds(subjectId),
                "questionCount", 9, "mode", "LEARNING"), 201).get("id").asLong();
        List<String> order = new ArrayList<>();
        call(get("/api/tests/" + sessionId), null, 200).get("questions")
                .forEach(q -> order.add(q.get("difficulty").asText()));
        List<String> rank = List.of("EASY", "MEDIUM", "HARD");
        List<String> sorted = new ArrayList<>(order);
        sorted.sort(Comparator.comparing(rank::indexOf));
        assertThat(order).isEqualTo(sorted);
        // yangi mavzuda oson savollar ustun: har mavzudan 3 tadan, demo ma'lumotda har mavzuda kamida 3 ta oson
        assertThat(order).containsOnly("EASY");
    }

    /** Savolga ataylab to'g'ri yoki xato javob beradi (to'g'ri variant bazadan olinadi). */
    private void answer(long sessionId, JsonNode q, boolean correct) throws Exception {
        Long right = jdbc.queryForObject("""
                select o.id from answer_options o
                join session_questions sq on sq.question_id = o.question_id
                where sq.id = ? and o.is_correct""", Long.class, q.get("id").asLong());
        long optionId = right;
        if (!correct) {
            for (JsonNode o : q.get("options")) {
                if (o.get("id").asLong() != right) {
                    optionId = o.get("id").asLong();
                    break;
                }
            }
        }
        call(post("/api/tests/" + sessionId + "/answers"),
                Map.of("sessionQuestionId", q.get("id").asLong(), "optionId", optionId), 200);
    }

    private JsonNode firstSubject() throws Exception {
        JsonNode subjects = call(get("/api/subjects"), null, 200);
        assertThat(subjects.size()).isGreaterThanOrEqualTo(2);
        return subjects.get(0);
    }

    private List<Long> topicIds(long subjectId) throws Exception {
        List<Long> ids = new ArrayList<>();
        call(get("/api/subjects/" + subjectId + "/topics"), null, 200).forEach(t -> ids.add(t.get("id").asLong()));
        return ids;
    }

    private JsonNode call(MockHttpServletRequestBuilder req, Object body, int expectedStatus) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        String content = mvc.perform(req).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? null : json.readTree(content);
    }
}
