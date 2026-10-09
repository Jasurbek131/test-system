package uz.testsystem.stats;

import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record SubjectStat(Long subjectId, String subjectName, long tests, long answered, long correct,
                              double percent) {
    }

    public record TopicStat(Long subjectId, String subjectName, Long topicId, String topicName, long answered,
                            long correct, double percent) {
    }

    public record MyStats(long totalTests, long finishedTests, long totalAnswered, long totalCorrect,
                          double overallPercent, double averageScore,
                          List<SubjectStat> bySubject, List<TopicStat> byTopic) {
    }
}
