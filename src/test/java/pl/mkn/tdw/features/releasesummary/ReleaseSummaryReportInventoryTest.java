package pl.mkn.tdw.features.releasesummary;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.releasesummary.context.LiquibaseDocumentParser;
import pl.mkn.tdw.features.releasesummary.report.ReleaseSummaryReportInventory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseSummaryReportInventoryTest {
    final ReleaseSummaryReportInventory inventory=new ReleaseSummaryReportInventory(new LiquibaseDocumentParser());
    @Test void crmObjectsHaveOneRowPerColumnAndCompoundIndexAndSeparateRollback() {
        var groups=inventory.groups(ReleaseSummaryCrmFixture.evidence());assertEquals(3,groups.size());
        assertEquals(List.of("customer","customer_tag",""),groups.stream().map(ReleaseSummaryReportInventory.Group::table).toList());
        var items=groups.stream().flatMap(g->g.items().stream()).toList();
        var columns=items.stream().filter(i->i.label().startsWith("Kolumna ")).toList();
        assertEquals(Set.of("Kolumna customer_tag.id","Kolumna customer_tag.customer_id","Kolumna customer_tag.label"),
                new HashSet<>(columns.stream().map(ReleaseSummaryReportInventory.Item::label).toList()));
        var id=columns.stream().filter(i->i.label().endsWith(".id")).findFirst().orElseThrow();
        assertTrue(id.definition().contains("bigint"));assertTrue(id.definition().contains("NOT NULL"));assertTrue(id.definition().contains("klucz główny"));
        var index=items.stream().filter(i->i.label().startsWith("Indeks ")).findFirst().orElseThrow();
        assertTrue(index.definition().contains("unikalny"));assertTrue(index.definition().contains("kolumny w kolejności: customer_id, label"));
        assertEquals(1,items.stream().filter(i->i.label().startsWith("Klucz obcy ")).count());
        assertEquals(1,items.stream().filter(i->i.label().startsWith("Dane w tabeli customer")).count());
        assertEquals(2,items.stream().filter(i->i.label().startsWith("Wycofanie:")).count());
        assertTrue(items.stream().noneMatch(i->i.label().contains("constraints")||i.label().contains("/databaseChangeLog")));
    }
    @Test void aConstraintOnlyEditIncludesUnchangedColumnTypeAndBeforeAfterValues() {
        var before="<databaseChangeLog><changeSet id=\"crm\" author=\"crm\"><createTable tableName=\"customer\"><column name=\"label\" type=\"varchar(40)\"><constraints nullable=\"true\"/></column></createTable></changeSet></databaseChangeLog>";
        var evidence=ReleaseSummaryCrmFixture.evidence("db/customer.xml",before,before.replace("nullable=\"true\"","nullable=\"false\""));
        var items=inventory.groups(evidence).get(0).items();assertEquals(1,items.size());
        assertEquals("Kolumna customer.label",items.get(0).label());
        assertTrue(items.get(0).definition().contains("Przed: typ: varchar(40); NULL dozwolony"));
        assertTrue(items.get(0).definition().contains("Po: typ: varchar(40); NOT NULL"));
    }
    @Test void yamlColumnAndConstraintsAreOneReadableObjectWithoutContainerRows() {
        var yaml="""
                databaseChangeLog:
                  - changeSet:
                      id: crm-label
                      author: crm
                      changes:
                        - addColumn:
                            tableName: customer
                            columns:
                              - column:
                                  name: label
                                  type: VARCHAR(40)
                                  constraints:
                                    nullable: true
                """;
        var items=inventory.groups(ReleaseSummaryCrmFixture.evidence("db/customer.yml","databaseChangeLog: []",yaml)).get(0).items();
        assertEquals(1,items.stream().filter(i->i.label().startsWith("Kolumna ")).count());
        var column=items.stream().filter(i->i.label().equals("Kolumna customer.label")).findFirst().orElseThrow();
        assertTrue(column.definition().contains("NULL dozwolony"));assertTrue(column.definition().contains("VARCHAR(40)"));
    }
    @Test void parserBatchesNeverSplitTheReaderTableOrLoseAnyOf120Columns() {
        var xml=new StringBuilder("<databaseChangeLog><changeSet id=\"crm\" author=\"crm\"><createTable tableName=\"customer_profile\">");
        for(int i=0;i<120;i++)xml.append("<column name=\"field_").append(i).append("\" type=\"varchar(40)\"><constraints nullable=\"false\"/></column>");
        xml.append("</createTable></changeSet></databaseChangeLog>");
        var evidence=ReleaseSummaryCrmFixture.evidence("db/customer.xml","<databaseChangeLog/>",xml.toString());
        assertTrue(evidence.units().size()>1);var groups=inventory.groups(evidence).stream().filter(g->!g.appendix()).toList();assertEquals(1,groups.size());
        var columns=groups.get(0).items().stream().filter(i->i.label().startsWith("Kolumna ")).toList();assertEquals(120,columns.size());
        for(int i=0;i<120;i++){var expected="Kolumna customer_profile.field_"+i;assertEquals(1,columns.stream().filter(c->c.label().equals(expected)).count());}
        assertTrue(columns.stream().allMatch(c->c.definition().contains("NOT NULL")));
    }
    @Test void removingTheChangelogDoesNotDescribeAnExecutedDrop() {
        var groups=inventory.groups(ReleaseSummaryCrmFixture.evidence("db/customer.xml",ReleaseSummaryCrmFixture.XML,"<databaseChangeLog/>"));
        var columns=groups.stream().flatMap(g->g.items().stream()).filter(i->i.label().startsWith("Kolumna ")).toList();
        assertEquals(3,columns.size());
        assertTrue(columns.stream()
                .allMatch(i->i.change().equals("Usunięcie definicji")||i.change().equals("Usunięcie z definicji wdrożenia")));
        assertTrue(groups.get(0).items().stream().noneMatch(i->i.change().contains("DROP")));
    }
    @Test void indexColumnOrderOnlyChangeIsACompleteIndexDefinition() {
        var before="<databaseChangeLog><changeSet id=\"crm\" author=\"crm\"><createIndex tableName=\"customer\" indexName=\"ix_customer_name\"><column name=\"last_name\"/><column name=\"first_name\"/></createIndex></changeSet></databaseChangeLog>";
        var after=before.replace("<column name=\"last_name\"/><column name=\"first_name\"/>","<column name=\"first_name\"/><column name=\"last_name\"/>");
        var items=inventory.groups(ReleaseSummaryCrmFixture.evidence("db/customer.xml",before,after)).get(0).items();
        assertEquals(1,items.size());assertEquals("Indeks customer.ix_customer_name",items.get(0).label());
        assertTrue(items.get(0).definition().contains("Przed: kolumny w kolejności: last_name, first_name"));
        assertTrue(items.get(0).definition().contains("Po: kolumny w kolejności: first_name, last_name"));
    }
    @Test void mergesTheSameTableAcrossFilesButKeepsSchemasAndRepositoriesIndependent() {
        var first=ReleaseSummaryCrmFixture.evidence("db/labels.xml",null,"<databaseChangeLog><changeSet id=\"labels\"><addColumn tableName=\"customer\"><column name=\"label\" type=\"text\"/></addColumn></changeSet></databaseChangeLog>");
        var second=ReleaseSummaryCrmFixture.evidence("db/status.xml",null,"<databaseChangeLog><changeSet id=\"status\"><addColumn tableName=\"customer\"><column name=\"status\" type=\"text\"/></addColumn><addColumn tableName=\"customer\" schemaName=\"archive\"><column name=\"status\" type=\"text\"/></addColumn></changeSet></databaseChangeLog>");
        var other=first.units().stream().map(u->new pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence.Unit(u.unitId()+"other","other-crm",u.path(),u.changeType(),u.baselineSource(),u.targetSource(),u.elements(),u.structurallyVerified())).toList();
        var otherSources=first.sources().stream().map(s->new pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence.Source(s.sourceRef(),"other-crm",s.project(),s.side(),s.commitId(),s.path(),s.blobId(),s.content(),s.dependencies())).toList();
        var sources=new ArrayList<>(first.sources());sources.addAll(second.sources());sources.addAll(otherSources);
        var units=new ArrayList<>(first.units());units.addAll(second.units());units.addAll(other);
        var evidence=new pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence(sources,units,List.of(),List.of(),3,true);
        var groups=inventory.groups(evidence).stream().filter(g->!g.appendix()).toList();assertEquals(3,groups.size());
        var customer=groups.stream().filter(g->g.table().equals("customer")&&g.repositoryId().equals("customer")).findFirst().orElseThrow();
        assertEquals(Set.of("Kolumna customer.label","Kolumna customer.status"),new HashSet<>(customer.items().stream().map(ReleaseSummaryReportInventory.Item::label).toList()));
        assertTrue(groups.stream().anyMatch(g->g.table().equals("archive.customer")));
        assertEquals(3,groups.stream().map(ReleaseSummaryReportInventory.Group::title).distinct().count());
    }
    @Test void unresolvedSqlRemainsInAppendixAndTargetedSqlNeverClaimsStructuralCompleteness() {
        var evidence=ReleaseSummaryCrmFixture.evidence("db/customer.sql",null,"--liquibase formatted sql\n--changeset crm:labels\nALTER TABLE customer ADD label TEXT;");
        var groups=inventory.groups(evidence);assertEquals("customer",groups.get(0).table());assertFalse(groups.get(0).structurallyVerified());
        var unknown=ReleaseSummaryCrmFixture.evidence("db/customer.sql",null,"--liquibase formatted sql\n--changeset crm:labels\nCALL refresh_customer_labels();");
        assertTrue(inventory.groups(unknown).stream().allMatch(ReleaseSummaryReportInventory.Group::appendix));
    }
}
