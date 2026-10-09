package uz.testsystem.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionPickerTest {

    private static List<Long> range(long from, long toExclusive) {
        return LongStream.range(from, toExclusive).boxed().toList();
    }

    private static Map<Long, Long> countByTopic(List<Long> picked, Map<Long, List<Long>> source) {
        Map<Long, Long> counts = new HashMap<>();
        source.forEach((topic, ids) -> counts.put(topic,
                picked.stream().filter(ids::contains).count()));
        return counts;
    }

    @RepeatedTest(20)
    void distributesEvenlyAcrossTopics() {
        Map<Long, List<Long>> source = Map.of(
                1L, range(100, 120),
                2L, range(200, 220),
                3L, range(300, 320));

        List<Long> picked = QuestionPicker.pick(source, 9, new Random());

        assertThat(picked).hasSize(9).doesNotHaveDuplicates();
        assertThat(countByTopic(picked, source).values()).containsOnly(3L);
    }

    @RepeatedTest(20)
    void remainderDiffersByAtMostOne() {
        Map<Long, List<Long>> source = Map.of(
                1L, range(100, 120),
                2L, range(200, 220),
                3L, range(300, 320));

        List<Long> picked = QuestionPicker.pick(source, 10, new Random());

        Collection<Long> counts = countByTopic(picked, source).values();
        assertThat(Collections.max(counts) - Collections.min(counts)).isLessThanOrEqualTo(1);
    }

    @RepeatedTest(20)
    void fillsFromOtherTopicsWhenOneRunsOut() {
        Map<Long, List<Long>> source = Map.of(
                1L, range(100, 102),   // faqat 2 ta savol
                2L, range(200, 220));

        List<Long> picked = QuestionPicker.pick(source, 10, new Random());

        Map<Long, Long> counts = countByTopic(picked, source);
        assertThat(picked).hasSize(10).doesNotHaveDuplicates();
        assertThat(counts.get(1L)).isEqualTo(2);
        assertThat(counts.get(2L)).isEqualTo(8);
    }

    @Test
    void canTakeAllAvailableQuestions() {
        Map<Long, List<Long>> source = Map.of(1L, range(1, 4), 2L, range(10, 12), 3L, List.of());

        List<Long> picked = QuestionPicker.pick(source, 5, new Random());

        assertThat(picked).containsExactlyInAnyOrder(1L, 2L, 3L, 10L, 11L);
    }

    @Test
    void rejectsInvalidCount() {
        Map<Long, List<Long>> source = Map.of(1L, range(1, 4));

        assertThatThrownBy(() -> QuestionPicker.pick(source, 4, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuestionPicker.pick(source, 0, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void percentRoundsToOneDecimal() {
        assertThat(TestService.percent(1, 3)).isEqualTo(33.3);
        assertThat(TestService.percent(2, 3)).isEqualTo(66.7);
        assertThat(TestService.percent(0, 0)).isEqualTo(0);
        assertThat(TestService.percent(5, 5)).isEqualTo(100);
    }
}
