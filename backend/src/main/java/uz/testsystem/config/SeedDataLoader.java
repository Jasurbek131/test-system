package uz.testsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import uz.testsystem.importer.ImportDtos.ImportReport;
import uz.testsystem.importer.ImportDtos.ImportRequest;
import uz.testsystem.importer.ImportService;
import uz.testsystem.subject.SubjectRepository;

import java.io.InputStream;

/**
 * Ishga tushishda seed fayllardagi fanlarni yuklaydi (admin import formatida).
 * Har bir fayl alohida tekshiriladi: shu nomli fan bazada bo'lsa, fayl o'tkazib yuboriladi.
 * Shuning uchun yangi fan (masalan, 6-sinf biologiya) uchun seed papkaga yangi fayl qo'shish yetarli.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SeedDataLoader implements ApplicationRunner {

    private final AppProperties props;
    private final SubjectRepository subjectRepository;
    private final ImportService importService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!props.seed().enabled()) {
            return;
        }
        Resource[] files = new PathMatchingResourcePatternResolver().getResources(props.seed().location());
        for (Resource file : files) {
            ImportRequest request;
            try (InputStream in = file.getInputStream()) {
                request = objectMapper.readValue(in, ImportRequest.class);
            }
            if (request.subject() == null || subjectRepository.findByNameIgnoreCase(request.subject().trim()).isPresent()) {
                continue;
            }
            ImportReport report = importService.importQuestions(request);
            log.info("Seed yuklandi: {} – {} ta mavzu, {} ta savol, {} ta xato",
                    file.getFilename(), report.topicsCreated(), report.questionsImported(), report.errors().size());
        }
    }
}
