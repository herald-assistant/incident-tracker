package pl.mkn.tdw.features.releasesummary.api;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryJobService;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRef;
import java.util.List;
@RestController
@RequestMapping("/api/release-summary")
@RequiredArgsConstructor
public class ReleaseSummaryController {
    private final ReleaseSummaryCatalog catalog;
    private final ReleaseSummaryJobService jobs;
    @GetMapping("/applications") public List<ReleaseSummaryCatalog.Application> applications() { return catalog.applications(); }
    @GetMapping("/applications/{systemId}/refs")
    public ReleaseSummaryCatalog.RefOptions refs(@PathVariable String systemId,
            @RequestParam(defaultValue="BRANCH") GitLabRef.Kind kind,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="1") int page) {
        if(page<1 || search.length()>255) throw new ReleaseSummaryException("INVALID_REF_QUERY","Nieprawidłowy filtr refów.");
        return catalog.refs(systemId,kind,search,page);
    }
    @PostMapping("/jobs") public ResponseEntity<ReleaseSummarySnapshot> start(@Valid @RequestBody ReleaseSummaryRequest request) {
        return ResponseEntity.accepted().body(jobs.start(request));
    }
    @GetMapping("/jobs/{jobId}") public ReleaseSummarySnapshot get(@PathVariable String jobId) { return jobs.get(jobId); }
}

