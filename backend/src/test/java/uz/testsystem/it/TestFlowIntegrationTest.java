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
