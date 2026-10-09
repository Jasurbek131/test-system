package uz.testsystem.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.testsystem.importer.ImportDtos.ImportReport;
import uz.testsystem.importer.ImportDtos.ImportRequest;
import uz.testsystem.importer.ImportService;

@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
public class AdminImportController {

    private final ImportService importService;

    @PostMapping
    public ImportReport importQuestions(@RequestBody ImportRequest req) {
        return importService.importQuestions(req);
    }
}
