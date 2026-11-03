package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.api.ApiExceptionHandler;
import pl.mkn.tdw.features.releasesummary.api.ReleaseSummaryController;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryCatalog;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryJobService;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.*;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReleaseSummarySelectionTest {
    @Test void previewsTheEffectiveCommitsAndValidatesTheDateSelection() throws Exception {
        var catalog=mock(ReleaseSummaryCatalog.class);
        var range=ReleaseSummaryCrmFixture.range();
        when(catalog.preview("crm",range.selection())).thenReturn(range);
        var mvc=MockMvcBuilders.standaloneSetup(new ReleaseSummaryController(catalog,mock(ReleaseSummaryJobService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/release-summary/applications/crm/range-preview").contentType("application/json")
                .content("""
                    {"branch":"refs/heads/release","dateFrom":"2026-10-01","dateTo":"2026-10-31","timeZone":"Europe/Warsaw"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selection.branch").value("refs/heads/release"))
                .andExpect(jsonPath("$.baseline.id").value(ReleaseSummaryCrmFixture.BEFORE))
                .andExpect(jsonPath("$.target.title").value("Dodanie etykiety klienta"));
        verify(catalog).preview("crm",range.selection());
        for(var body:List.of(
                "{\"branch\":\"release\",\"dateFrom\":\"2026-11-01\",\"dateTo\":\"2026-10-01\",\"timeZone\":\"UTC\"}",
                "{\"branch\":\"release\",\"dateFrom\":\"2026-10-01\",\"dateTo\":\"2026-10-31\",\"timeZone\":\"unknown\"}",
                "{\"branch\":\"../main\",\"dateFrom\":\"2026-10-01\",\"dateTo\":\"2026-10-31\",\"timeZone\":\"UTC\"}"))
            mvc.perform(post("/api/release-summary/applications/crm/range-preview").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        mvc.perform(post("/api/release-summary/jobs").contentType("application/json").content("""
                {"systemId":"crm","mode":"LIQUIBASE","baselineRef":{"kind":"BRANCH","name":"release"},
                 "targetRef":{"kind":"TAG","name":"next"}}
                """ )).andExpect(status().isBadRequest());
    }
    @Test void chatIsReadOnlyByDefaultAndRequiresExplicitMutationFlag() throws Exception {
        var jobs=mock(ReleaseSummaryJobService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ReleaseSummaryController(mock(ReleaseSummaryCatalog.class),jobs))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/release-summary/jobs/crm/chat/messages").contentType("application/json")
                .content("{\"message\":\"Wyjaśnij kolumnę\"}")).andExpect(status().isAccepted());
        verify(jobs).chat("crm","Wyjaśnij kolumnę",false);
        mvc.perform(post("/api/release-summary/jobs/crm/chat/messages").contentType("application/json")
                .content("{\"message\":\"Popraw opis\",\"updateReport\":true}")).andExpect(status().isAccepted());
        verify(jobs).chat("crm","Popraw opis",true);
        mvc.perform(post("/api/release-summary/jobs/crm/chat/messages").contentType("application/json")
                .content("{\"message\":\" \"}")).andExpect(status().isBadRequest());
    }
    @Test void typedRefsAreDistinctAndCannotEscape() {
        assertEquals("refs/heads/release/CRM",new GitLabRef(GitLabRef.Kind.BRANCH,"release/CRM").canonical());
        assertEquals("refs/tags/release/CRM",new GitLabRef(GitLabRef.Kind.TAG,"release/CRM").canonical());
        for(var bad:List.of("../main","a..b","a b","/main","a.lock","a\\b","a@{x}"))
            assertThrows(IllegalArgumentException.class,()->new GitLabRef(GitLabRef.Kind.BRANCH,bad));
        assertThrows(IllegalArgumentException.class,()->new GitLabRef(GitLabRef.Kind.COMMIT,"abc"));
    }
    @Test void startReturnsAcceptedSnapshotBeforeAsyncResolution() throws Exception {
        var catalog=mock(ReleaseSummaryCatalog.class);
        var tasks=new ArrayList<Runnable>();
        var service=new ReleaseSummaryJobService(catalog,mock(pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryDiffCollector.class),
                mock(pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryPreparation.class),
                mock(pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryPrompt.class),mock(pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportService.class),
                mock(pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryAiRunner.class),mock(pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryPersistence.class),
                ()->pl.mkn.tdw.shared.ai.AnalysisAiAuthRef.localToken("CRM"),new pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunOperationGuard(),tasks::add);
        var mvc=MockMvcBuilders.standaloneSetup(new ReleaseSummaryController(catalog,service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var mapper=new ObjectMapper().findAndRegisterModules();
        mvc.perform(post("/api/release-summary/jobs").contentType("application/json").content(mapper.writeValueAsString(
                new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,ReleaseSummaryCrmFixture.range(),null,null)))).andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"));
        assertEquals(1,tasks.size());
        verifyNoInteractions(catalog);
        mvc.perform(post("/api/release-summary/jobs").contentType("application/json").content("""
                {"systemId":"crm","mode":"LIQUIBASE"}
                """)).andExpect(status().isBadRequest());
    }
}

