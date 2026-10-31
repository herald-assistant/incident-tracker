package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.context.*;
import pl.mkn.tdw.features.releasesummary.contract.*;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySnapshotPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryReadPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFilePort;
import pl.mkn.tdw.integrations.gitlab.contract.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiquibaseEvidenceCollectorTest {
    @Test void identicalPinnedCommitsReuseOneVerifiedInventory() {
        var input=Map.of(ROOT,xml(""));var fixture=fixture(input,input);
        var scope=new ReleaseSummaryScope("crm","CRM","CRM","digest",List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),
                "refs/heads/current",BEFORE,"refs/tags/next",BEFORE)),List.of());
        var evidence=fixture.collector.collect(scope);assertTrue(evidence.noChanges());assertEquals(1,evidence.scannedFiles());
        verify(fixture.files,times(1)).readComplete(any(),anyString(),anyString(),eq(BEFORE),eq(ROOT));
    }
    @Test void largeMigrationIsPartitionedWithoutLosingColumnsOrChangingStableIds() {
        var xml=new StringBuilder("<databaseChangeLog><changeSet id=\"crm-fields\" author=\"crm\"><createTable tableName=\"customer\">");
        for(int i=0;i<120;i++)xml.append("<column name=\"field_").append(i).append("\" type=\"varchar(40)\"/>");
        xml.append("</createTable></changeSet></databaseChangeLog>");
        var input=Map.of("db/customer.xml",xml.toString());var evidence=collect(Map.of(),input);
        assertTrue(evidence.sourceComplete());assertTrue(evidence.units().size()>1);
        assertTrue(evidence.units().stream().allMatch(u->u.elements().size()<=32));
        var columns=evidence.units().stream().flatMap(u->u.elements().stream()).filter(e->e.kind().equals("column")).toList();
        assertEquals(120,columns.size());assertEquals(120,columns.stream().map(e->e.after().get("name")).distinct().count());
        assertEquals(evidence.units(),collect(Map.of(),input).units());
    }
    @Test void changedGitlinksAndLfsSourcesCannotDisappearFromCoverage() {
        var snapshots=mock(GitLabRepositorySnapshotPort.class);
        when(snapshots.snapshot("CRM","customer",BEFORE)).thenReturn(new GitLabRepositorySnapshot("CRM","customer",BEFORE,Map.of(),Map.of("db/module",BEFORE),Map.of()));
        when(snapshots.snapshot("CRM","customer",AFTER)).thenReturn(new GitLabRepositorySnapshot("CRM","customer",AFTER,Map.of(),Map.of("db/module",AFTER),Map.of()));
        var collector=new LiquibaseEvidenceCollector(snapshots,mock(GitLabRepositoryReadPort.class),mock(GitLabVerifiedRepositoryFilePort.class),new LiquibaseDocumentParser());
        assertFalse(collector.collect(scope()).sourceComplete());
        when(snapshots.snapshot("CRM","customer",AFTER)).thenReturn(new GitLabRepositorySnapshot("CRM","customer",AFTER,Map.of(),Map.of("db/module",BEFORE),Map.of()));
        assertTrue(collector.collect(scope()).noChanges());
        assertFalse(collect(Map.of(),Map.of("db/change.xml","version https://git-lfs.github.com/spec/v1\noid sha256:fixture\nsize 128")).sourceComplete());
    }
    @Test void optionalMissingIncludeAllIsCompleteButDynamicAndCustomReferencesAreNot() {
        var optional=Map.of("db/root.xml",xml("<includeAll path=\"optional\" relativeToChangelogFile=\"true\" errorIfMissingOrEmpty=\"false\"/>"));
        assertTrue(collect(optional,optional).noChanges());
        for(var reference:List.of("<include file=\"${crm.file}\"/>","<includeAll path=\"db\" filter=\"crm.CustomFilter\"/>")) {
            var evidence=collect(Map.of(),Map.of("db/root.xml",xml(reference)));
            assertFalse(evidence.sourceComplete());assertFalse(evidence.noChanges());
        }
    }
    @Test void transportFailuresNeverLookLikeAnEmptyComparison() {
        for(int status:List.of(401,403,404,429,504)) {
            var fixture=fixture(Map.of(),Map.of(ROOT,xml("")));
            when(fixture.files.readComplete(any(),anyString(),anyString(),anyString(),anyString())).thenThrow(
                    new org.springframework.web.client.RestClientResponseException("CRM failure",status,"fixture",null,null,null));
            var evidence=fixture.collector.collect(scope());assertFalse(evidence.sourceComplete());assertFalse(evidence.noChanges());
        }
    }
    static final String BEFORE="a".repeat(40), AFTER="b".repeat(40);
    private static final String ROOT="src/main/resources/db/root.xml";
    private static final String CHANGE="src/main/resources/db/customer.xml";
    static String xml(String body) {return "<databaseChangeLog>"+body+"</databaseChangeLog>";}
    static String include(String file) {return "<include file=\""+file+"\" relativeToChangelogFile=\"true\"/>";}

    @Test void collectsChangedCsvAndSqlBehindUnchangedChangelog() {
        var changelog=xml("<changeSet id=\"customer-labels\" author=\"crm\"><loadData tableName=\"customer_label\" file=\"labels.csv\" relativeToChangelogFile=\"true\"/><sqlFile path=\"index.sql\" relativeToChangelogFile=\"true\"/></changeSet>");
        var before=Map.of(ROOT,xml(include("customer.xml")),CHANGE,changelog,
                "src/main/resources/db/labels.csv","id,label\n1,standard\n",
                "src/main/resources/db/index.sql","create index ix_label on customer_label(label);");
        var after=new HashMap<>(before);
        after.put("src/main/resources/db/labels.csv","id,label\n1,priority\n");
        after.put("src/main/resources/db/index.sql","create unique index ix_label on customer_label(label);");
        var evidence=collect(before,after);
        assertTrue(evidence.sourceComplete(),evidence.gaps().toString());
        assertEquals(Set.of(ROOT,CHANGE,"src/main/resources/db/labels.csv","src/main/resources/db/index.sql"),
                new HashSet<>(evidence.units().stream().map(ReleaseSummaryEvidence.Unit::path).toList()));
        assertEquals(8,evidence.sources().size());
        assertTrue(evidence.units().stream().filter(u->u.path().endsWith(".sql")).noneMatch(ReleaseSummaryEvidence.Unit::structurallyVerified));
    }
    @Test void seesNewInclusionOfUnchangedFileAndEveryDeclaredColumnAndIndex() {
        var change=xml("<changeSet id=\"customer\" author=\"crm\"><createTable tableName=\"customer\"><column name=\"id\" type=\"bigint\"><constraints primaryKey=\"true\" nullable=\"false\"/></column><column name=\"display_name\" type=\"varchar(120)\"/></createTable><createIndex indexName=\"ix_customer_name\" tableName=\"customer\"><column name=\"display_name\"/></createIndex></changeSet>");
        var evidence=collect(Map.of(ROOT,xml(""),CHANGE,change),Map.of(ROOT,xml(include("customer.xml")),CHANGE,change));
        var unit=evidence.units().stream().filter(u->u.path().equals(CHANGE)).findFirst().orElseThrow();
        assertEquals("INCLUSION_CHANGED",unit.changeType());
        assertEquals(3,unit.elements().stream().filter(e->e.kind().equals("column")).count());
        assertTrue(unit.elements().stream().anyMatch(e->e.kind().equals("constraints")));
        assertTrue(unit.elements().stream().anyMatch(e->e.kind().equals("createIndex")));
    }
    @Test void includeAllCapturesNewRawSqlAndRemoval() {
        var root=xml("<includeAll path=\"changes\" relativeToChangelogFile=\"true\" endsWithFilter=\".sql\"/>");
        var evidence=collect(Map.of(ROOT,root,"src/main/resources/db/changes/01.sql","alter table customer add label varchar(20);"),
                Map.of(ROOT,root,"src/main/resources/db/changes/02.sql","alter table customer add name varchar(30);"));
        assertTrue(evidence.sourceComplete(),evidence.gaps().toString());
        assertEquals(Set.of("ADDED","REMOVED","DEPENDENCY_CHANGED"),new HashSet<>(evidence.units().stream().map(ReleaseSummaryEvidence.Unit::changeType).toList()));
    }
    @Test void missingReferenceCycleAndInvalidXmlCannotReportNoChanges() {
        for(var input:List.of(Map.of(ROOT,xml(include("missing.xml"))),Map.of(ROOT,xml(include("root.xml"))),
                Map.of(ROOT,"<!DOCTYPE databaseChangeLog [<!ENTITY x SYSTEM \"file:///private.txt\">]><databaseChangeLog>&x;</databaseChangeLog>"))) {
            var evidence=collect(input,input);
            assertFalse(evidence.sourceComplete());
            assertFalse(evidence.noChanges());
            assertTrue(evidence.gaps().stream().anyMatch(ReleaseSummaryEvidence.Gap::blocking));
        }
    }
    @Test void identicalCompleteInputsAreNoChangesAndUnrelatedFilesStayOutOfPrompt() {
        var input=Map.of(ROOT,xml("<changeSet id=\"crm\" author=\"team\"><createTable tableName=\"customer\"/></changeSet>"),
                "pom.xml","<project/>");
        var evidence=collect(input,input);
        assertTrue(evidence.noChanges());
        assertEquals(0,evidence.sources().size());
        assertEquals(4,evidence.scannedFiles());
    }
    @Test void yamlJsonAndFormattedSqlRetainMetadataAndRollbackDependency() {
        var parser=new LiquibaseDocumentParser();
        for(var text:List.of("databaseChangeLog:\n  - changeSet:\n      id: customer\n      author: crm\n      runOnChange: true\n      changes:\n        - addColumn:\n            tableName: customer\n            columns:\n              - column:\n                  name: label\n                  type: varchar(20)\n",
                "{\"databaseChangeLog\":[{\"changeSet\":{\"id\":\"customer\",\"author\":\"crm\",\"changes\":[{\"addColumn\":{\"tableName\":\"customer\",\"columns\":[{\"column\":{\"name\":\"label\",\"type\":\"varchar(20)\"}}]}}]}}]}")) {
            var parsed=parser.parse("change.yaml",text);
            assertTrue(parsed.changelog());
            assertTrue(parser.flatten(parsed).values().stream().anyMatch(n->n.kind().equals("column")&&n.attributes().get("name").equals("label")));
        }
        var evidence=collect(Map.of(),Map.of("db/root.sql","--liquibase formatted sql\n--changeset crm:customer\ncreate table customer(id bigint);\n--rollbackSqlFile path:rollback.sql relativeToChangelogFile:true\n",
                "db/rollback.sql","drop table customer;"));
        assertTrue(evidence.sourceComplete(),evidence.gaps().toString());
        assertTrue(evidence.sources().stream().anyMatch(s->s.path().equals("db/rollback.sql")));
    }
    @Test void mismatchedBlobIsBlocking() {
        var fixture=fixture(Map.of(),Map.of(ROOT,xml("")));
        when(fixture.files.readComplete(any(),anyString(),anyString(),anyString(),anyString()))
                .thenReturn(new GitLabVerifiedFile(ROOT,xml("changed"),42));
        assertFalse(fixture.collector.collect(scope()).sourceComplete());
    }
    @Test void symbolicDirectoryCannotBecomeNoChanges() {
        var snapshots=mock(GitLabRepositorySnapshotPort.class);
        when(snapshots.snapshot(eq("CRM"),eq("customer"),anyString())).thenAnswer(i->new GitLabRepositorySnapshot("CRM","customer",i.getArgument(2),Map.of(),Map.of(),Map.of("db/shared",BEFORE)));
        var collector=new LiquibaseEvidenceCollector(snapshots,mock(GitLabRepositoryReadPort.class),mock(GitLabVerifiedRepositoryFilePort.class),new LiquibaseDocumentParser());
        var result=collector.collect(scope());
        assertFalse(result.noChanges());assertFalse(result.sourceComplete());
        assertTrue(result.gaps().stream().anyMatch(g->g.code().equals("SYMLINK_UNRESOLVED")));
    }
    private ReleaseSummaryEvidence collect(Map<String,String> before,Map<String,String> after) {return fixture(before,after).collector.collect(scope());}
    private record Fixture(LiquibaseEvidenceCollector collector,GitLabVerifiedRepositoryFilePort files) {}
    private Fixture fixture(Map<String,String> before,Map<String,String> after) {
        var snapshots=mock(GitLabRepositorySnapshotPort.class); var files=mock(GitLabVerifiedRepositoryFilePort.class);
        when(snapshots.snapshot("CRM","customer",BEFORE)).thenReturn(snapshot(before,BEFORE));
        when(snapshots.snapshot("CRM","customer",AFTER)).thenReturn(snapshot(after,AFTER));
        when(files.readComplete(any(),eq("CRM"),eq("customer"),anyString(),anyString())).thenAnswer(i->{
            var path=(String)i.getArgument(4); var text=(i.getArgument(3).equals(BEFORE)?before:after).get(path);
            if(text==null) throw new IllegalStateException("Missing CRM fixture");
            return new GitLabVerifiedFile(path,text,text.getBytes(StandardCharsets.UTF_8).length);
        });
        return new Fixture(new LiquibaseEvidenceCollector(snapshots,mock(GitLabRepositoryReadPort.class),files,new LiquibaseDocumentParser()),files);
    }
    private GitLabRepositorySnapshot snapshot(Map<String,String> files,String sha) {
        var blobs=new HashMap<String,String>(); files.forEach((path,text)->blobs.put(path,blob(text)));
        return new GitLabRepositorySnapshot("CRM","customer",sha,blobs,Map.of(),Map.of());
    }
    static String blob(String text) {
        try {
            var bytes=text.getBytes(StandardCharsets.UTF_8); var digest=MessageDigest.getInstance("SHA-1");
            digest.update(("blob "+bytes.length+"\0").getBytes(StandardCharsets.UTF_8)); digest.update(bytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
    private ReleaseSummaryScope scope() {
        return new ReleaseSummaryScope("crm","CRM","CRM","digest",List.of(new ReleaseSummaryScope.Repository("customer","customer",List.of(""),
                "refs/heads/current",BEFORE,"refs/tags/next",AFTER)),List.of());
    }
}

