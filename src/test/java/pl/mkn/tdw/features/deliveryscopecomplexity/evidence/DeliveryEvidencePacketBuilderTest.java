package pl.mkn.tdw.features.deliveryscopecomplexity.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryscopecomplexity.deliveryunit.DeliveryUnit;
import pl.mkn.tdw.features.deliveryscopecomplexity.source.DeliveryScopeIssue;
import pl.mkn.tdw.integrations.jira.contract.JiraIssueMaterial;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.mergeRequest;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.unit;
import static pl.mkn.tdw.features.deliveryscopecomplexity.DeliveryScopeTestFixtures.material;

class DeliveryEvidencePacketBuilderTest {

    @Test
    void shouldBuildScorablePacketWithoutPersonalDataOrStoryPoints() {
        var builder = new DeliveryEvidencePacketBuilder();

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}")
        ));
        var allArtifacts = String.join("\n", packet.artifacts().values());

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isFalse();
        assertThat(allArtifacts)
                .contains("CRM-123", "CustomerStatus")
                .doesNotContain(
                        "jira-comment-author-1",
                        "mr-author-101",
                        "internal comment payload",
                        "Story Points",
                        "timeSpentSeconds",
                        "originalEstimateSeconds",
                        "remainingEstimateSeconds",
                        "14400",
                        "28800"
                );
    }

    @Test
    void shouldExcludeMechanicalOnlyChangesBeforeAi() {
        var builder = new DeliveryEvidencePacketBuilder();

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "frontend/dist/main.min.js", "+minified")
        ));

        assertThat(packet.scorable()).isTrue();
        assertThat(packet.mechanicallyExcluded()).isTrue();
    }

    @Test
    void shouldPreserveCompleteDiffWithoutLocalCharacterBudget() {
        var builder = new DeliveryEvidencePacketBuilder();
        var completeDiff = "x".repeat(75_000);

        var packet = builder.build(unit(
                "CRM-123",
                mergeRequest(7, "src/main/java/CustomerStatus.java", completeDiff)
        ));

        assertThat(packet.artifacts().get("delivery-scope-complexity/diffs.md"))
                .contains(completeDiff);
        assertThat(packet.visibilityLimits())
                .noneMatch(limit -> limit.contains("truncated"));
    }
    @Test
    void shouldProduceIdenticalAiInputForStandardAndCustomTypes() {
        var standardChild = material("CRM-124", "Sub-task", List.of(), null, List.of());
        var customChild = material("CRM-124", "Sub Task dev", List.of(), null, List.of());
        var standardParent = material("CRM-123", "Story", List.of(standardChild), null, List.of());
        var customParent = material("CRM-123", "Dev Story", List.of(customChild), null, List.of());
        var builder = new DeliveryEvidencePacketBuilder();

        var standard = builder.build(unitWithMaterials(standardParent, standardChild));
        var custom = builder.build(unitWithMaterials(customParent, customChild));

        assertThat(custom.artifacts()).isEqualTo(standard.artifacts());
        var issues = custom.artifacts().get("delivery-scope-complexity/issues.md");
        assertThat(issues).contains(
                "Oceniane zadania: CRM-123, CRM-124",
                "Zadanie nadrzedne wobec: CRM-124",
                "Zadanie podrzedne wobec: CRM-123");
        assertThat(issues).doesNotContain("Type:", "Story", "Sub-task", "Dev Story", "Sub Task dev");
        assertThat(StringUtils.countOccurrencesOf(issues, "## CRM-124\n")).isEqualTo(1);
    }

    @Test
    void shouldKeepSharedParentAndOtherChildrenOutsideAssessmentAndDeduplicateDocuments() {
        var parent = material("CRM-100", "CRM Delivery", List.of(), null, List.of("Parent intent is partial."));
        var otherChild = material("CRM-201", "CRM Implementation", List.of(), null, List.of("Child is not visible."));
        var first = material("CRM-124", "CRM Implementation", List.of(otherChild), parent, List.of());
        var second = material("CRM-125", "CRM Implementation", List.of(), parent, List.of());

        var packet = new DeliveryEvidencePacketBuilder().build(unitWithMaterials(first, second));
        var issues = packet.artifacts().get("delivery-scope-complexity/issues.md");

        assertThat(issues).contains(
                "Oceniane zadania: CRM-124, CRM-125",
                "## CRM-100\n\n- Zakres oceny: KONTEKST POZA ZAKRESEM OCENY",
                "Zadanie nadrzedne wobec: CRM-124, CRM-125",
                "## CRM-201\n\n- Zakres oceny: KONTEKST POZA ZAKRESEM OCENY",
                "Zadanie podrzedne wobec: CRM-124");
        assertThat(StringUtils.countOccurrencesOf(issues, "## CRM-100\n")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(issues, "Customer status follows the eligibility decision."))
                .isEqualTo(1);
        assertThat(issues).contains("Referenced by tasks: CRM-124, CRM-125, CRM-100, CRM-201");
        assertThat(String.join("\n", packet.artifacts().values()))
                .doesNotContain("internal comment payload", "jira-comment-author-1", "mr-author-101", "14400", "28800");
        assertThat(packet.visibilityLimits()).contains(
                "Jira issue CRM-100: Parent intent is partial.",
                "Jira issue CRM-201: Child is not visible.");
        assertThat(packet.unit().issues()).extracting(DeliveryScopeIssue::issueKey).containsExactly("CRM-124", "CRM-125");
    }

    @Test
    void shouldNotInferHierarchyFromTypeNameForStandaloneTask() {
        var packet = new DeliveryEvidencePacketBuilder().build(unitWithMaterials(
                material("CRM-123", "Sub Task dev", List.of(), null, List.of())));

        assertThat(packet.artifacts().get("delivery-scope-complexity/issues.md"))
                .contains("OCENIANE ZADANIE", "brak potwierdzonego parent/child")
                .doesNotContain("Sub Task dev", "Zadanie nadrzedne wobec:", "Zadanie podrzedne wobec:");
        assertThat(packet.visibilityLimits()).isEmpty();
    }

    @Test
    void shouldRetainDistinctDescriptionsForOneTaskSeenInAssessmentAndParentContext() {
        var parent = material("CRM-100");
        var revisedParent = new JiraIssueMaterial(
                parent.issueKey(), parent.issueUrl(), parent.summary(), "Additional customer status intent.",
                parent.issueType(), parent.status(), parent.labels(), parent.acceptanceCriteria(), parent.links(),
                parent.subTasks(), parent.parentIssue(), parent.confluencePages(), parent.comments(), parent.limitations(),
                parent.customFields(), parent.timeTracking());
        var child = material("CRM-124", "CRM Implementation", List.of(), revisedParent, List.of());

        var packet = new DeliveryEvidencePacketBuilder().build(unitWithMaterials(parent, child));
        var issues = packet.artifacts().get("delivery-scope-complexity/issues.md");

        assertThat(StringUtils.countOccurrencesOf(issues, "## CRM-100\n")).isEqualTo(1);
        assertThat(issues).contains(parent.description(), revisedParent.description(),
                "## CRM-100\n\n- Zakres oceny: OCENIANE ZADANIE");
    }

    @Test
    void shouldPreserveSeparateParentsForTasksConnectedByMergeRequests() {
        var firstParent = material("CRM-100");
        var secondParent = material("CRM-200");
        var packet = new DeliveryEvidencePacketBuilder().build(unitWithMaterials(
                material("CRM-124", "CRM Implementation", List.of(), firstParent, List.of()),
                material("CRM-125", "CRM Implementation", List.of(), secondParent, List.of())));

        assertThat(packet.artifacts().get("delivery-scope-complexity/issues.md")).contains(
                "Oceniane zadania: CRM-124, CRM-125",
                "Zadanie podrzedne wobec: CRM-100",
                "Zadanie podrzedne wobec: CRM-200",
                "## CRM-100\n\n- Zakres oceny: KONTEKST POZA ZAKRESEM OCENY",
                "## CRM-200\n\n- Zakres oceny: KONTEKST POZA ZAKRESEM OCENY");
    }

    private DeliveryUnit unitWithMaterials(JiraIssueMaterial... materials) {
        return new DeliveryUnit("DU-CRM", Arrays.stream(materials)
                .map(material -> new DeliveryScopeIssue(material.issueKey(),
                        Instant.parse("2026-07-10T10:00:00Z"), material, List.of())).toList(),
                List.of(mergeRequest(7, "src/main/java/CustomerStatus.java", "+class CustomerStatus {}")),
                List.of());
    }
}
