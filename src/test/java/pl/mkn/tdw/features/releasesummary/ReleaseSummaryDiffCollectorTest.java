package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryDiffCollector;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryDiffProperties;
import pl.mkn.tdw.features.releasesummary.context.ReleaseSummaryPreparation;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.features.releasesummary.job.ReleaseSummaryProgressEvidence;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryComparisonPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryReadPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFilePort;
import pl.mkn.tdw.integrations.gitlab.GitLabInstructionContextPort;
import pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionContextResult;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryComparison;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabVerifiedFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseSummaryDiffCollectorTest {
    final GitLabRepositoryComparisonPort port=mock(GitLabRepositoryComparisonPort.class);
    final ReleaseSummaryDiffProperties properties=new ReleaseSummaryDiffProperties();
    final ReleaseSummaryDiffCollector collector=new ReleaseSummaryDiffCollector(port,properties);
    final ReleaseSummaryScope scope=ReleaseSummaryReportTest.SCOPE;
    final GitLabRepositoryReadPort files=mock(GitLabRepositoryReadPort.class);
    final GitLabVerifiedRepositoryFilePort verified=mock(GitLabVerifiedRepositoryFilePort.class);
    final GitLabInstructionContextPort instructions=mock(GitLabInstructionContextPort.class);
    final ReleaseSummaryPreparation preparation=new ReleaseSummaryPreparation(files,verified,instructions);
    @BeforeEach void noInstructions() {when(instructions.discoverApplicable(any())).thenReturn(new InstructionContextResult(List.of(),List.of()));}
    void content(String path,String commit,String text) {
        when(verified.readComplete(files,"CRM","customer",commit,path)).thenReturn(
                new GitLabVerifiedFile(path,text,text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
    }
    GitLabRepositoryComparison.Change change(String oldPath,String newPath,boolean patch) {
        return new GitLabRepositoryComparison.Change(oldPath,newPath,oldPath==null?null:"c".repeat(40),newPath==null?null:"d".repeat(40),
                oldPath==null?null:"100644",newPath==null?null:"100644",patch?"@@ -1 +1 @@\n-old\n+new":null,patch);
    }
    void given(List<GitLabRepositoryComparison.Change> changes,boolean complete) {
        when(port.compare("CRM","customer","a".repeat(40),"b".repeat(40))).thenReturn(new GitLabRepositoryComparison("a".repeat(40),"b".repeat(40),changes,complete,List.of()));
    }
    @Test void keepsOnlyMatchingChangesAndTheirPatches() {
        given(List.of(change("db/changelog/master.xml","db/changelog/master.xml",true),change("data/labels.csv","data/labels.csv",true),
                change(null,"src/Customer.java",true),change("config/settings.properties",null,true)),true);
        var result=collector.collect(scope);assertTrue(result.changeListComplete());assertEquals(1,result.changes().size());
        assertTrue(result.changes().get(0).change().patchComplete());
        assertTrue(result.gaps().isEmpty());verify(port).compare("CRM","customer","a".repeat(40),"b".repeat(40));verifyNoMoreInteractions(port);
    }
    @Test void missingPatchIsAnAiResearchGapNotABlockingDiscoveryFailure() {
        given(List.of(change(null,"db/changelog/labels.csv",false)),true);var result=collector.collect(scope);
        assertTrue(result.changeListComplete());assertFalse(result.noChanges());assertFalse(result.gaps().get(0).blocking());
        var fixture=new ReleaseSummaryContinuationTest.Fixture();when(fixture.collector.collect(any())).thenReturn(result);
        var job=fixture.service.start(fixture.request);fixture.next();verify(fixture.ai).execute(any());assertNotEquals("BLOCKED",fixture.service.get(job.jobId()).status());
    }
    @Test void invalidOrFailedManifestNeverMeansNoChanges() {
        given(List.of(),false);var incomplete=collector.collect(scope);assertFalse(incomplete.changeListComplete());assertFalse(incomplete.noChanges());
        when(port.compare(anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("CRM transport failure"));
        var failure=collector.collect(scope);assertFalse(failure.changeListComplete());assertTrue(failure.gaps().get(0).blocking());
    }
    @Test void emptyVerifiedManifestMeansNoChanges() {given(List.of(),true);assertTrue(collector.collect(scope).noChanges());}
    @Test void renameAcrossScopeKeepsBothPathsAndPrefixMatchingUsesSegmentBoundaries() {
        given(List.of(change("db/changelog/customer.xml","archive/customer.xml",true),change(null,"database/changelog/customer.xml",true)),true);
        var selected=new ReleaseSummaryScope(scope.systemId(),scope.systemName(),scope.group(),scope.catalogDigest(),
                List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of("db"),"a".repeat(40),"a".repeat(40),"b".repeat(40),"b".repeat(40))),scope.entities());
        var result=collector.collect(selected);assertEquals(1,result.changes().size());assertEquals("archive/customer.xml",result.changes().get(0).change().newPath());
    }
    @Test void progressCallsAPatchAPatchNeverFullBeforeOrAfterContent() {
        given(List.of(change("db/changelog/customer.xml",null,true)),true);var result=collector.collect(scope);
        var attributes=ReleaseSummaryProgressEvidence.sections(scope,result).get(0).items().get(0).attributes();
        assertTrue(attributes.stream().anyMatch(attribute->attribute.name().equals("Patch")));
        assertFalse(attributes.stream().anyMatch(attribute->Set.of("beforeContent","afterContent").contains(attribute.name())));
    }
    @Test void preparesOnlyTheSelectedDiffInAHundredModuleRepository() {
        var changes=new ArrayList<GitLabRepositoryComparison.Change>();
        for(int module=0;module<100;module++)changes.add(change("module-"+module+"/pom.xml","module-"+module+"/pom.xml",false));
        var path="module-customer/src/main/resources/db/changelog/customer.sql";
        changes.add(change(path,path,false));given(changes,true);
        content(path,"a".repeat(40),"UPDATE CUSTOMER SET LABEL = 'Before';\n");
        content(path,"b".repeat(40),"UPDATE CUSTOMER SET LABEL = 'After';\n");
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertEquals(1,prepared.evidence().changes().size());assertTrue(prepared.evidence().gaps().isEmpty());
        var selected=prepared.evidence().changes().get(0).change();
        assertTrue(selected.patchComplete());assertTrue(selected.patch().contains("-UPDATE CUSTOMER SET LABEL = 'Before';"));
        assertTrue(selected.patch().contains("+UPDATE CUSTOMER SET LABEL = 'After';"));assertEquals(2,prepared.sources().size());
        verify(verified).readComplete(files,"CRM","customer","a".repeat(40),path);
        verify(verified).readComplete(files,"CRM","customer","b".repeat(40),path);verifyNoMoreInteractions(verified);
        verifyNoInteractions(files);
    }
    @Test void completePatchDoesNotReadAnyContent() {
        given(List.of(change("db/changelog/customer.sql","db/changelog/customer.sql",true)),true);
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().gaps().isEmpty());assertTrue(prepared.sources().isEmpty());verifyNoInteractions(files,verified);
    }
    @Test void additionsAndDeletionsReadOnlyTheirExistingSideAndPreserveFinalNewline() {
        var added="db/changelog/added.sql";var removed="db/changelog/removed.sql";
        given(List.of(change(null,added,false),change(removed,null,false)),true);
        content(added,"b".repeat(40),"CREATE TABLE CUSTOMER (ID INT);");
        content(removed,"a".repeat(40),"CREATE TABLE CUSTOMER_LABEL (ID INT);\n");
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().gaps().isEmpty());assertEquals(2,prepared.sources().size());
        assertTrue(prepared.evidence().changes().get(0).change().patch().contains("\\ No newline at end of file"));
        verify(verified).readComplete(files,"CRM","customer","b".repeat(40),added);
        verify(verified).readComplete(files,"CRM","customer","a".repeat(40),removed);verifyNoMoreInteractions(verified);
    }
    @Test void failedSelectedReadRemainsAnExplicitGapWithoutLosingTheManifest() {
        var path="db/changelog/customer.sql";given(List.of(change(path,path,false)),true);
        when(verified.readComplete(files,"CRM","customer","a".repeat(40),path)).thenThrow(new IllegalStateException("Unavailable"));
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().changeListComplete());assertFalse(prepared.evidence().noChanges());
        assertEquals("PATCH_INCOMPLETE",prepared.evidence().gaps().get(0).code());assertTrue(prepared.sources().isEmpty());
    }
    @Test void matchesBothRenameSidesAndPreservesUnicodeAndLineEndings() {
        var oldPath="db/CHANGELOG/customer.sql";var newPath="archive/customer.sql";
        given(List.of(change(oldPath,newPath,false)),true);
        content(oldPath,"a".repeat(40),"UPDATE CUSTOMER SET LABEL = '\u017b\u00f3\u0142ty';\r\n");
        content(newPath,"b".repeat(40),"UPDATE CUSTOMER SET LABEL = 'Nowy';\r\n");
        var prepared=preparation.prepare(scope,collector.collect(scope));
        var patch=prepared.evidence().changes().get(0).change().patch();
        assertTrue(prepared.evidence().gaps().isEmpty());assertTrue(patch.contains("--- a/"+oldPath));
        assertTrue(patch.contains("+++ b/"+newPath));assertTrue(patch.contains("'\u017b\u00f3\u0142ty';\r"));
        verify(instructions).discoverApplicable(argThat(request->request.scopes().get(0).ref().equals("b".repeat(40))
                &&request.scopes().get(0).changedFilePaths().equals(List.of(oldPath,newPath))));
    }
    @Test void modeOnlyAndContentIdenticalRenamesDoNotReadBlobs() {
        var path="db/changelog/customer.sql";var blob="c".repeat(40);
        given(List.of(new GitLabRepositoryComparison.Change(path,path,blob,blob,"100644","100755",null,false),
                new GitLabRepositoryComparison.Change(path,"archive/customer.sql",blob,blob,"100644","100644",null,false)),true);
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().gaps().isEmpty());assertTrue(prepared.sources().isEmpty());
        assertTrue(prepared.evidence().changes().get(0).change().patch().contains("new mode 100755"));
        assertTrue(prepared.evidence().changes().get(1).change().patch().contains("rename to archive/customer.sql"));
        verifyNoInteractions(files,verified);
    }
    @Test void symlinksBinaryAndLfsAreNotPresentedAsCompleteTextDiffs() {
        var link="db/changelog/link.sql";var binary="db/changelog/binary.sql";var lfs="db/changelog/large.sql";
        given(List.of(new GitLabRepositoryComparison.Change(null,link,null,"d".repeat(40),null,"120000",null,false),
                change(null,binary,false),change(null,lfs,false)),true);
        content(binary,"b".repeat(40),"binary\u0000data");content(lfs,"b".repeat(40),"version https://git-lfs.github.com/spec/v1\noid sha256:fictional\n");
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertEquals(3,prepared.evidence().gaps().size());assertTrue(prepared.sources().isEmpty());
        assertTrue(prepared.evidence().changes().stream().noneMatch(file->file.change().patchComplete()));
        verify(verified,never()).readComplete(any(),anyString(),anyString(),anyString(),eq(link));
    }
    @Test void nonMatchingChangesProduceNoChangesWithoutReadsOrInstructions() {
        given(List.of(change("module/pom.xml","module/pom.xml",false)),true);
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().gaps().isEmpty());assertTrue(prepared.evidence().noChanges());
        verifyNoInteractions(files,verified,instructions);
    }
    @Test void preparedInstructionsAreCitableOnlyAtTheSelectedTargetRevision() {
        var path="db/changelog/customer.sql";given(List.of(change(path,path,true)),true);
        var correct=new pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionSource("CRM/customer","b".repeat(40),
                ".github/sql.instructions.md","SCOPED_INSTRUCTIONS","CRM labels.",false,null,List.of(path));
        var wrong=new pl.mkn.tdw.integrations.gitlab.contract.instructions.InstructionSource("CRM/customer","a".repeat(40),
                ".github/old.instructions.md","SCOPED_INSTRUCTIONS","Old labels.",false,null,List.of(path));
        when(instructions.discoverApplicable(any())).thenReturn(new InstructionContextResult(List.of(correct,wrong),List.of()));
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertEquals(List.of(correct),prepared.instructions());assertEquals(1,prepared.sources().size());
        assertTrue(prepared.sources().contains("gitlab:CRM/customer@"+"b".repeat(40)+":.github/sql.instructions.md"));
        assertEquals("INSTRUCTION_LIMITED",prepared.evidence().gaps().get(0).code());verifyNoInteractions(files,verified);
    }
    @Test void customKeywordsMatchBothRenameSidesWithTrimAndCase() {
        properties.setPathContainsAny(List.of(" MIGRATIONS ", "schema"));
        given(List.of(change("db/migrations/customer.yml","archive/customer.yml",true),
                change("archive/contact.json","db/SCHEMA/contact.json",true),
                change(null,"db/sql/ignored.sql",true)),true);
        var result=collector.collect(scope);
        assertEquals(2,result.changes().size());
        assertTrue(result.changes().stream().allMatch(file->file.change().patchComplete()));
    }
    @Test void defaultSqlSelectsMigrationAndExcludesUnrelatedCodeConfigAndFixturesEverywhere() throws Exception {
        var migration="src/main/resources/SQL/customer.yml";
        given(List.of(change(null,migration,true),change(null,"src/Customer.java",true),
                change(null,"application.yml",true),change(null,"fixtures/customer.json",true),
                change(null,"module-customer/src/test/resources/sql/customer.yml",false),
                change(null,"module-customer/SRC/TEST/resources/changelog/customer.xml",false)),true);
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertEquals(1,prepared.evidence().changes().size());
        verify(instructions).discoverApplicable(argThat(request->request.scopes().get(0).changedFilePaths().equals(List.of(migration))));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var reports=ReleaseSummaryReportTest.reports(mapper);
        var prompt=new pl.mkn.tdw.features.releasesummary.ai.ReleaseSummaryPrompt(mapper,reports).render(scope,prepared);
        var exported=mapper.writeValueAsString(prepared.evidence());
        var progress=mapper.writeValueAsString(ReleaseSummaryProgressEvidence.sections(scope,prepared.evidence()));
        for(var excluded:List.of("src/Customer.java","application.yml","fixtures/customer.json",
                "module-customer/src/test/resources/sql/customer.yml","module-customer/SRC/TEST/resources/changelog/customer.xml")) {
            assertFalse(prompt.contains(excluded));assertFalse(exported.contains(excluded));assertFalse(progress.contains(excluded));
        }
        assertTrue(prompt.contains(migration));verifyNoInteractions(files,verified);
    }
    @Test void testPathsOverridePositiveMatchesOnEitherSideWithoutReadingSources() {
        var tests="module-customer/src/test/resources/sql/customer.yml";
        var main="module-customer/src/main/resources/sql/customer.yml";
        var upper="module-customer/SRC/TEST/resources/CHANGELOG/customer.xml";
        given(List.of(change(null,tests,false),change(tests,null,false),change(tests,tests,false),
                change(tests,main,false),change(main,tests,false),change(null,upper,false)),true);
        var prepared=preparation.prepare(scope,collector.collect(scope));
        assertTrue(prepared.evidence().noChanges());assertTrue(prepared.evidence().gaps().isEmpty());
        verifyNoInteractions(files,verified,instructions);
        properties.setPathContainsAny(List.of("customer"));
        assertTrue(collector.collect(scope).noChanges());
    }

    @Test void noChangesReportDoesNotClaimIdenticalRevisions() {
        given(List.of(change(null,"src/Customer.java",true)),true);
        assertTrue(collector.collect(scope).noChanges());
        var report=ReleaseSummaryReportTest.reports(new com.fasterxml.jackson.databind.ObjectMapper()).noChanges("report",scope);
        assertTrue(report.sections().get(0).markdown().contains("poza filtrem"));
    }

}