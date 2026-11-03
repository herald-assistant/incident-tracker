package pl.mkn.tdw.features.releasesummary.api;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryJobService;
import java.util.List;
@RestController
@RequestMapping("/api/release-summary")
@RequiredArgsConstructor
public class ReleaseSummaryController {
    private final ReleaseSummaryCatalog catalog;
    private final ReleaseSummaryJobService jobs;
    @GetMapping("/applications") public List<ReleaseSummaryCatalog.Application> applications() { return catalog.applications(); }
    @GetMapping("/applications/{systemId}/branches")
    public ReleaseSummaryCatalog.RefOptions branches(@PathVariable String systemId,
            @RequestParam(defaultValue="") String search, @RequestParam(defaultValue="1") int page) {
        if(page<1 || search.length()>255) throw new ReleaseSummaryException("INVALID_BRANCH_QUERY", "Nieprawidłowy filtr branchy.");
        return catalog.branches(systemId, search, page);
    }
    @PostMapping("/applications/{systemId}/range-preview")
    public ReleaseSummaryRange preview(@PathVariable String systemId, @Valid @RequestBody ReleaseSummaryDateSelection selection) {
        return catalog.preview(systemId, selection);
    }
    @PostMapping("/jobs") public ResponseEntity<ReleaseSummarySnapshot> start(@Valid @RequestBody ReleaseSummaryRequest request) {
        return ResponseEntity.accepted().body(jobs.start(request));
    }
    @GetMapping("/jobs/{jobId}") public ReleaseSummarySnapshot get(@PathVariable String jobId) { return jobs.get(jobId); }
    public record ChatRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=20000) String message,boolean updateReport) {}
    @PostMapping("/jobs/{jobId}/chat/messages")
    public ResponseEntity<ReleaseSummarySnapshot> chat(@PathVariable String jobId,@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.accepted().body(jobs.chat(jobId,request.message(),request.updateReport()));
    }
    @PutMapping("/jobs/{jobId}/report")
    public ReleaseSummarySnapshot edit(@PathVariable String jobId,@RequestBody pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest request) {
        return jobs.edit(jobId,request);
    }
    @GetMapping("/jobs/{jobId}/export")
    public pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryPersistence.Envelope export(@PathVariable String jobId) {return jobs.export(jobId);}
}

