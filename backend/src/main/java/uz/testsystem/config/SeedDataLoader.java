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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ishga tushishda seed fayllardagi fanlarni yuklaydi (admin import formatida).
 * Har bir fayl alohida tekshiriladi: shu nomli fan bazada bo'lsa, fayl o'tkazib yuboriladi.
 * Shuning uchun yangi fan (masalan, 6-sinf biologiya) uchun seed papkaga yangi fayl qo'shish yetarli:
 * u nomi bo'yicha guruhga ("6-sinf Biologiya" -> "Biologiya") avtomatik qo'shiladi.
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
        Map<Resource, ImportRequest> requests = new LinkedHashMap<>();
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                requests.put(file, objectMapper.readValue(in, ImportRequest.class));
            }
        }
        // Guruh balli guruh yaratilganda olinadi: ball ko'rsatilgan fayllar birinchi yuklansin, aks holda
        // masalan "2-sinf Matematika" oldin yuklanib, Matematika guruhi standart 1.0 ball bilan yaratilib qolardi
        List<Resource> ordered = new ArrayList<>(requests.keySet());
        ordered.sort(Comparator.comparing(f -> requests.get(f).scoreWeight() == null));
        for (Resource file : ordered) {
            ImportRequest request = requests.get(file);
            if (request.subject() == null || subjectRepository.findByNameIgnoreCase(request.subject().trim()).isPresent()) {
                continue;
            }
            ImportReport report = importService.importQuestions(request);
            log.info("Seed yuklandi: {} – {} ta mavzu, {} ta savol, {} ta xato",
                    file.getFilename(), report.topicsCreated(), report.questionsImported(), report.errors().size());
        }
    }
}
