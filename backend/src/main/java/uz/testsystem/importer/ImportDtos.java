package uz.testsystem.importer;

import uz.testsystem.question.Difficulty;

import java.util.List;

/**
 * Savollarni ommaviy import qilish formati (README da namuna bor).
 * Fan va mavzular nomi bo'yicha qidiriladi, topilmasa yaratiladi.
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    public record ImportOption(String text, boolean correct) {
    }

    public record ImportQuestion(String text, String explanation, Difficulty difficulty, List<ImportOption> options) {
    }

    public record ImportTopic(String name, String description, List<ImportQuestion> questions) {
    }

    /**
     * {@code group} – ixtiyoriy, blok test guruhi (berilmasa fan nomidan: "2-sinf Matematika" -> "Matematika").
     * {@code scoreWeight} – ixtiyoriy, guruhning 1 savol balli; faqat guruh yangi yaratilganda qo'llanadi.
     */
    public record ImportRequest(String subject, String subjectDescription, String group, Double scoreWeight,
                                List<ImportTopic> topics) {
    }

    public record ImportError(String path, String message) {
    }

    public record ImportReport(Long subjectId, boolean subjectCreated, int topicsCreated, int questionsImported,
                               int questionsSkipped, List<ImportError> errors) {
    }
}
