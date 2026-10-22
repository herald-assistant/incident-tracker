package pl.mkn.tdw.integrations.operationalcontext.adapter.local;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.yaml.snakeyaml.Yaml;
import pl.mkn.tdw.integrations.operationalcontext.config.OperationalContextProperties;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.internal.OperationalContextRawDocuments;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextCatalogValidationService;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextReadModelValidator;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextValidationBaselineLoader;
import pl.mkn.tdw.testsupport.operationalcontext.OperationalContextMaintenanceExamples;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.testsupport.operationalcontext.OperationalContextMaintenanceExamples.*;

class OperationalContextMaintenanceExamplesTest {
    @TempDir Path temporaryDirectory;

    @ParameterizedTest
    @EnumSource(OperationalContextCatalogEntityType.class)
    void everyMainExamplePassesStrictMaintenancePreviewWithoutLosingFields(OperationalContextCatalogEntityType type) {
        var harness = harness();
        var before = harness.store().currentStoredSnapshot();
        for (var entity : entities(decoded(documents()), type)) {
            assertThat(harness.service().validatePartialEditablePayload(type.externalName(), entity))
                    .as("%s:%s shape", type, entity.get("id")).isEmpty();
            var preview = harness.service().previewUpdate(new OperationalContextCatalogMutationCommand(
                    type.externalName(), entity.get("id").toString(), entity));
            assertThat(preview.violations()).as("%s:%s full preview", type, entity.get("id")).isEmpty();
            assertThat(preview.candidatePayload()).isEqualTo(entity);
        }
        assertThat(harness.store().currentStoredSnapshot().rawDocuments()).isEqualTo(before.rawDocuments());
    }

    @Test
    void nineExamplesFormAClosedCatalogWithoutErrorsOrDuplicateWarnings() {
        var snapshot = harness().store().currentStoredSnapshot();
        assertThat(new OperationalContextReadModelValidator().validate(snapshot.readSnapshot().catalog())).isEmpty();
        assertThat(snapshot.readSnapshot().catalog().openQuestions()).hasSize(3);
        assertThat(snapshot.decodedDocuments().get("glossary.yml")).containsKeys("schemaVersion", "catalogKind", "gaps");
        assertThat(snapshot.decodedDocuments().get("handoff-rules.yml")).containsKeys("schemaVersion", "catalogKind", "gaps");
    }

    @Test
    void externalSystemWithoutAccessibleCodeDoesNotRequireAnInventedScope() {
        var harness = harness();
        var payload = Map.<String, Object>of("id", "crm-partner-directory", "name", "CRM Partner Directory",
                "systemType", "external-saas", "summary", "External CRM directory with no accessible implementation code.");
        assertThat(harness.service().previewCreate(new OperationalContextCatalogMutationCommand(
                "system", "crm-partner-directory", payload)).violations()).isEmpty();
    }

    @Test
    void explicitOwnershipBlocksDeletingTheReferencedTeamWithoutReverseReferences() {
        var harness = harness();
        var before = harness.store().currentStoredSnapshot().rawDocuments();
        var impact = harness.service().deleteImpact("team", "crm-domain-team");
        assertThat(impact.allowed()).isFalse();
        assertThat(impact.inboundReferences()).hasSize(2).allSatisfy(reference -> {
            assertThat(reference.relationType()).isEqualTo("owned-by-team");
            assertThat(reference.fieldPath()).contains(".ownership.ownerTeamIds[id=crm-domain-team]");
        });
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> harness.service().delete("team", "crm-domain-team"))
                .isInstanceOf(pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogMaintenanceException.class);
        assertThat(harness.store().currentStoredSnapshot().rawDocuments()).isEqualTo(before);
    }

    @Test
    void updateAccountsForMeaningfulOmittedSourceTextWithoutActivatingASignal() {
        var raw = decoded(documents());
        entity(raw, OperationalContextCatalogEntityType.SYSTEM, "crm-contact-core").put("xCrmUnclassifiedEvidence",
                Map.of("label", "crm-agent-portal", "meaning", "Reviewed CRM build label; not a system identity."));
        var harness = harness(rendered(raw));
        var source = harness.store().currentStoredSnapshot();
        assertThat(source.decodedDocuments().get("systems.yml").toString()).contains("xCrmUnclassifiedEvidence");
        var payload = new LinkedHashMap<>(harness.service().writablePayloadForUpdate("system", "crm-contact-core"));
        assertThat(payload).doesNotContainKey("xCrmUnclassifiedEvidence");
        var notes = new java.util.ArrayList<Object>((List<?>) payload.get("notes"));
        notes.add("crm-agent-portal: Reviewed CRM build label; not a system identity.");
        payload.put("notes", notes);
        var preview = harness.service().previewUpdate(new OperationalContextCatalogMutationCommand("system", "crm-contact-core", payload));
        assertThat(preview.violations()).isEmpty();
        harness.service().update(new OperationalContextCatalogMutationCommand("system", "crm-contact-core", payload));
        var result = harness.store().currentStoredSnapshot();
        var originalSystem = source.readSnapshot().catalog().systems().stream().filter(system -> system.id().equals("crm-contact-core")).findFirst().orElseThrow();
        var updatedSystem = result.readSnapshot().catalog().systems().stream().filter(system -> system.id().equals("crm-contact-core")).findFirst().orElseThrow();
        assertThat(updatedSystem.genericSignals()).isEqualTo(originalSystem.genericSignals()).doesNotContain("crm-agent-portal");
        assertThat(result.rawDocuments().content("systems.yml"))
                .contains("crm-agent-portal: Reviewed CRM build label; not a system identity.")
                .doesNotContain("xCrmUnclassifiedEvidence");
    }

    @Test
    void additionalProcessSemanticsExamplePassesTheFullValidator() {
        var blocks = blocks(read("processes-yml-update-prompt.md"));
        assertThat(blocks).hasSize(2);
        var harness = harness();
        var payload = new LinkedHashMap<>(harness.service().writablePayloadForUpdate("process", "crm-contact-preference-update"));
        payload.putAll(yaml(blocks.get(1).body()));
        assertThat(harness.service().previewUpdate(new OperationalContextCatalogMutationCommand(
                "process", "crm-contact-preference-update", payload)).violations()).isEmpty();
    }

    @Test
    void everyFieldGuidanceSnippetPassesTheSchemaAndReferencesOfTheCrmCatalog() throws Exception {
        var examples = List.of(
                new Snippet("process", "crm-contact-preference-update", "references"),
                new Snippet("system", "crm-contact-core", "ownership"),
                new Snippet("system", "crm-contact-core", "matchSignals"),
                new Snippet("system", "crm-contact-core", "sourceCoverage"),
                new Snippet("system", "crm-contact-core", "gaps"),
                new Snippet("repository", "crm-contact-core", "git"),
                new Snippet("repository", "crm-contact-core", null),
                new Snippet("code-search-scope", "crm-contact-core-code-scope", "target"),
                new Snippet("code-search-scope", "crm-contact-core-code-scope", "repositories"),
                new Snippet("process", "crm-contact-preference-update", "processBoundary"),
                new Snippet("process", "crm-contact-preference-update", "lifecycle"),
                new Snippet("process", "crm-contact-preference-update", "completionSignals"),
                new Snippet("process", "crm-contact-preference-update", "failureModes"),
                new Snippet("process", "crm-contact-preference-update", "dataAndArtifacts"),
                new Snippet("process", "crm-contact-preference-update", "steps"),
                new Snippet("integration", "crm-contact-to-core", "participants"),
                new Snippet("integration", "crm-contact-to-core", "failureModes"),
                new Snippet("bounded-context", "crm-contact-preferences", null));
        var snippets = blocks(read("operational-context-field-guidance.md"));
        assertThat(snippets).as("every field snippet needs an explicit validation context").hasSize(examples.size());
        var harness = harness();
        var json = JsonMapper.builder().build();
        for (var index = 0; index < snippets.size(); index++) {
            var snippet = snippets.get(index);
            var context = examples.get(index);
            Object value = "json".equals(snippet.format())
                    ? json.readValue(snippet.body(), Object.class) : new Yaml().load(snippet.body());
            if ("steps".equals(context.field()) || "failureModes".equals(context.field()) && value instanceof Map<?, ?>)
                value = List.of(value);
            var payload = new LinkedHashMap<>(harness.service().writablePayloadForUpdate(context.type(), context.id()));
            if (context.field() != null) payload.put(context.field(), value);
            else payload.putAll(json.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            assertThat(harness.service().validatePartialEditablePayload(context.type(), payload))
                    .as("snippet %s %s.%s", index, context.type(), context.field()).isEmpty();
            assertThat(harness.service().previewUpdate(new OperationalContextCatalogMutationCommand(
                    context.type(), context.id(), payload)).violations()).as("snippet %s full preview", index).isEmpty();
        }
    }

    @Test
    void fieldGuidanceTablesOnlyAdvertiseWritableEntityFields() {
        var sections = Map.of("Systems", "system", "Repositories", "repository", "Code-search scopes", "code-search-scope",
                "Processes", "process", "Integrations", "integration", "Bounded contexts", "bounded-context",
                "Teams", "team", "Glossary terms", "glossary-term", "Handoff rules", "handoff-rule");
        var maintenance = harness().service();
        String type = null;
        boolean fields = false;
        for (var line : read("operational-context-field-guidance.md").split("\n")) {
            if (line.startsWith("## ")) { type = sections.get(line.substring(3)); fields = false; }
            if (line.startsWith("| Field |")) fields = true;
            if (line.startsWith("| Property |")) fields = false;
            if (type == null || !fields || !line.startsWith("| `")) continue;
            var field = line.substring(3, line.indexOf('`', 3)).split("\\.|\\[", 2)[0];
            assertThat(maintenance.validatePartialEditablePayload(type, Map.of(field, "CRM example")))
                    .as("%s table field %s", type, field)
                    .noneMatch(error -> error.message().equals("Unknown field is not writable"));
        }
    }

    private Harness harness() {
        return harness(documents());
    }

    private Harness harness(Map<String, String> documents) {
        var properties = new OperationalContextProperties();
        properties.setStorageDirectory(temporaryDirectory.resolve("catalog").toString());
        var source = (OperationalContextDocumentSource) () -> new OperationalContextRawDocuments("fictional-crm-examples", documents);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var validation = new OperationalContextCatalogValidationService(new OperationalContextValidationBaselineLoader(mapper, new DefaultResourceLoader()));
        var store = new DefaultOperationalContextSnapshotStore(new LocalOperationalContextStore(properties, source,
                new OperationalContextCatalogCodec(), new OperationalContextAtomicMover(), validation));
        return new Harness(new OperationalContextCatalogMaintenanceService(store, new OperationalContextYamlWriter()), store);
    }

    private record Harness(OperationalContextCatalogMaintenanceService service, DefaultOperationalContextSnapshotStore store) {}
    private record Snippet(String type, String id, String field) {}
}
