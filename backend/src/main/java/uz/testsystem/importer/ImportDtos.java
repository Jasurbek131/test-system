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

    public record ImportRequest(String subject, String subjectDescription, List<ImportTopic> topics) {
    }

    public record ImportError(String path, String message) {
    }

    public record ImportReport(Long subjectId, boolean subjectCreated, int topicsCreated, int questionsImported,
                               int questionsSkipped, List<ImportError> errors) {
    }
}
