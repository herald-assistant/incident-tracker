package pl.mkn.tdw.features.releasesummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.mkn.tdw.features.releasesummary.ai.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryPreparation;
import pl.mkn.tdw.features.releasesummary.report.*;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsProvider;
import pl.mkn.tdw.integrations.gitlab.*;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import pl.mkn.tdw.integrations.operationalcontext.contract.*;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"analysis.ai.copilot.telemetry.enabled=false"})
@Import(ReleaseSummaryRuntimeTest.CrmPorts.class)
class ReleaseSummaryRuntimeTest {
    static final String ROOT="target/release-summary-pilot/"+UUID.randomUUID();
    @DynamicPropertySource static void paths(DynamicPropertyRegistry registry) {
        registry.add("analysis.ai.copilot.copilot-home",()->ROOT+"/copilot");
        registry.add("analysis.ai.copilot.working-directory",()->ROOT+"/workspace");
    }
    @Autowired ReleaseSummaryAiRunner runner;
    @Autowired ReleaseSummaryReportService reports;
    @Autowired ReleaseSummaryPrompt prompts;
    @Autowired CopilotModelOptionsProvider models;
    @Autowired ObjectMapper mapper;
    @Test void realSpringToolRegistryContainsEntireReleaseSummaryCapability() {
        var context=context(new AtomicReference<>());
        var run=runner.assemble("crm-wiring",request("crm-model",null),prompts.render(context.scope,new ReleaseSummaryPreparation.Prepared(context.evidence,Set.of(),List.of())),
                AnalysisAiAuthRef.localToken("CRM"),context,reports.scaffold("crm-report",context.scope,context.evidence),null,e->{},e->{});
        assertEquals(ReleaseSummaryToolPolicy.ALLOWED,new HashSet<>(run.sessionConfigRequest().availableToolNames()));
        assertTrue(run.artifactContents().isEmpty());
    }
    @Test @EnabledIfEnvironmentVariable(named="RELEASE_SUMMARY_LIVE_TEST",matches="(?i)true")
    void liveModelDocumentsIndependentCrmMigrationAndReadsItsCode() throws Exception {
        var options=models.modelOptions(CopilotRunAuth.localToken());
        var selected=options.models().stream().filter(m->m.id().equals(options.defaultModel())).findFirst()
                .orElseGet(()->options.models().stream().filter(m->m.supportsReasoningEffort()).findFirst().orElseThrow());
        var effort=selected.reasoningEfforts().contains("high")?"high":null;
        var saved=new AtomicReference<AnalysisReport>();var context=context(saved);
        var id="crm-pilot-"+UUID.randomUUID();var initial=reports.scaffold(id,context.scope,context.evidence);
        var run=runner.assemble(id,request(selected.id(),effort),prompts.render(context.scope,new ReleaseSummaryPreparation.Prepared(context.evidence,Set.of(),List.of())),AnalysisAiAuthRef.localToken("CRM pilot"),
                context,initial,null,e->{},e->{});
        var result=runner.execute(run);
        assertTrue(context.reportMutated,"The real model must write report tools.");
        assertNotNull(result.report());assertNotNull(saved.get());
        var projection=reports.project(result.report(),context.evidence,context.sources());
        Files.createDirectories(Path.of("target/release-summary-pilot"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/release-summary-pilot/result.json").toFile(),
                Map.of("model",selected.id(),"report",result.report(),"result",projection,"sources",context.sources()));
        assertTrue(projection.documentedTables()>0,projection.visibilityLimits().toString());
        assertEquals("AI_ASSESSED",projection.semanticCoverage());
        assertTrue(context.sources().stream().anyMatch(ref->ref.endsWith(ReleaseSummaryCrmFixture.CODE_PATH)),"Functional reasoning must be grounded in read code.");
        assertFalse(result.report().sections().stream().anyMatch(section->section.markdown().contains("DO_UZUPELNIENIA")));
        System.out.printf("Release summary CRM live pilot: model=%s, status=%s, tables=%d, checkpoints persisted, source code read.%n",
                selected.id(),projection.status(),projection.documentedTables());
    }
    private ReleaseSummaryRequest request(String model,String effort) {return new ReleaseSummaryRequest("crm",ReleaseSummaryRequest.Mode.LIQUIBASE,
            new GitLabRef(GitLabRef.Kind.BRANCH,"current"),new GitLabRef(GitLabRef.Kind.TAG,"next"),model,effort);}
    private ReleaseSummaryAiContext context(AtomicReference<AnalysisReport> saved) {
        var scope=ReleaseSummaryCrmFixture.scope();var evidence=ReleaseSummaryCrmFixture.evidence();
        var system=OperationalContextDtos.system(Map.of("id","crm","name","CRM Customers","summary","Etykiety klienta wspierają wyszukiwanie i filtry operatora."));
        var catalog=new OperationalContextDtos.OperationalContextCatalog(null,null,List.of(system),null,null,null,null,null,null,null,null);
        return new ReleaseSummaryAiContext(scope,evidence,OperationalContextReadSession.fromSnapshot(new OperationalContextSnapshot("digest","CRM fixture",catalog)),
                GitLabRepositoryToolScope.forRevisions("CRM",List.of(new GitLabRepositoryToolScope.PinnedRevision("customer","refs/heads/current",ReleaseSummaryCrmFixture.BEFORE),
                        new GitLabRepositoryToolScope.PinnedRevision("customer","refs/tags/next",ReleaseSummaryCrmFixture.AFTER))),
                reports.initialSources(scope,evidence),saved::set,()->{},true);
    }
    @TestConfiguration static class CrmPorts {
        @Bean @Primary GitLabRepositoryPort crmRepositoryPort() {
            var port=mock(GitLabRepositoryPort.class);
            when(port.listRepositoryFilesPage(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt())).thenAnswer(i->
                    new GitLabRepositoryFilePage(List.of(new GitLabRepositoryFile("CRM","customer",i.getArgument(2),ReleaseSummaryCrmFixture.PATH),
                            new GitLabRepositoryFile("CRM","customer",i.getArgument(2),ReleaseSummaryCrmFixture.CODE_PATH)),null));
            when(port.resolveRevision(anyString(),anyString(),anyString())).thenAnswer(i->new GitLabRepositoryRevision(i.getArgument(0),i.getArgument(1),i.getArgument(2),ReleaseSummaryCrmFixture.AFTER,null));
            return port;
        }
        @Bean @Primary GitLabVerifiedRepositoryFilePort crmVerifiedFiles() {
            var port=mock(GitLabVerifiedRepositoryFilePort.class);
            when(port.readComplete(any(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{
                String path=i.getArgument(4);
                var content=path.equals(ReleaseSummaryCrmFixture.CODE_PATH)?ReleaseSummaryCrmFixture.CODE:
                        path.equals(ReleaseSummaryCrmFixture.PATH)?(ReleaseSummaryCrmFixture.BEFORE.equals(i.getArgument(3))?"<databaseChangeLog/>":ReleaseSummaryCrmFixture.XML):null;
                if(content==null)throw new IllegalArgumentException("No such CRM fixture file.");
                return new GitLabVerifiedFile(path,content,content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            });return port;
        }
        @Bean @Primary GitLabRepositoryTreeExplorerPort crmTree() {
            return (group,project,commit,path,cursor)->new GitLabRepositoryTreeSlice("",4,List.of(
                    new GitLabRepositoryTreeSlice.Entry(ReleaseSummaryCrmFixture.PATH,"blob"),
                    new GitLabRepositoryTreeSlice.Entry(ReleaseSummaryCrmFixture.CODE_PATH,"blob")),List.of(),false);
        }
        @Bean @Primary GitLabRepositoryBranchPort crmBranches() {return (project,search)->new GitLabBranchPage(List.of(new GitLabBranch("current",true)),false);}
    }
}
