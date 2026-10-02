package in.ruko.api;

import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.api.dto.AnalyzeResponse;
import in.ruko.pipeline.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyze(request);
    }
}
