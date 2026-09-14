package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextCatalogNestedFieldsTest {

    @Test
    void rejectsAndRemovesUnknownSystemFieldsButKeepsDynamicSignals() {
        var payload = object(
                "runtime", object("configurationDirectory", "crm/service", "oldRuntimeFlag", true),
                "sourceCoverage", object("status", "partial", "oldProvenance", "unreviewed"),
                "matchSignals", object("strong", object("customTelemetryTag", List.of("crm-contact")))
        );
        var errors = new ArrayList<OperationalContextCatalogFieldError>();

        OperationalContextCatalogNestedFields.rejectUnknown(OperationalContextCatalogEntityType.SYSTEM, payload, errors);

        assertEquals(Set.of("/payload/runtime/oldRuntimeFlag", "/payload/sourceCoverage/oldProvenance"),
                errors.stream().map(OperationalContextCatalogFieldError::pointer).collect(java.util.stream.Collectors.toSet()));
        OperationalContextCatalogNestedFields.removeUnknown(OperationalContextCatalogEntityType.SYSTEM, payload);
        assertEquals(Map.of("configurationDirectory", "crm/service"), payload.get("runtime"));
        assertEquals(Map.of("status", "partial"), payload.get("sourceCoverage"));
        assertEquals(Map.of("strong", Map.of("customTelemetryTag", List.of("crm-contact"))), payload.get("matchSignals"));
    }

    @Test
    void cleansNestedProcessCardsIncludingObsoleteKeys() {
        var payload = object(
                "lifecycle", object(
                        "statuses", List.of("accepted"),
                        "triggers", List.of(object("type", "event", "name", "ContactChanged", "exchange", "crm.events", "oldFlag", "drop"))
                ),
                "steps", List.of(object(
                        "id", "accept-contact", "name", "Accept contact", "match", object("terms", List.of("contact")),
                        "references", object("systems", List.of("crm"), "unused", List.of("legacy"))
                )),
                "failureModes", List.of(object("id", "no-contact", "name", "No contact", "summary", "Missing", "obsoleteSeverity", "low"))
        );
        var errors = new ArrayList<OperationalContextCatalogFieldError>();

        OperationalContextCatalogNestedFields.rejectUnknown(OperationalContextCatalogEntityType.PROCESS, payload, errors);

        assertEquals(Set.of(
                "/payload/lifecycle/triggers/0/oldFlag",
                "/payload/steps/0/match",
                "/payload/steps/0/references/unused",
                "/payload/failureModes/0/obsoleteSeverity"
        ), errors.stream().map(OperationalContextCatalogFieldError::pointer).collect(java.util.stream.Collectors.toSet()));
        OperationalContextCatalogNestedFields.removeUnknown(OperationalContextCatalogEntityType.PROCESS, payload);
        var step = castMap(((List<?>) payload.get("steps")).get(0));
        assertFalse(step.containsKey("match"));
        assertFalse(castMap(step.get("references")).containsKey("unused"));
        assertFalse(castMap(((List<?>) payload.get("failureModes")).get(0)).containsKey("obsoleteSeverity"));
    }

    @Test
    void cleansIntegrationParticipantCardsIncludingObsoleteRepositories() {
        var payload = object("participants", object(
                "source", object("system", "crm", "repositories", List.of("crm-repo"), "unusedScope", "old"),
                "targets", List.of(object("system", "profiles", "role", "server", "debugNotes", "old"))
        ));
        var errors = new ArrayList<OperationalContextCatalogFieldError>();

        OperationalContextCatalogNestedFields.rejectUnknown(OperationalContextCatalogEntityType.INTEGRATION, payload, errors);

        assertEquals(Set.of("/payload/participants/source/repositories", "/payload/participants/source/unusedScope", "/payload/participants/targets/0/debugNotes"),
                errors.stream().map(OperationalContextCatalogFieldError::pointer).collect(java.util.stream.Collectors.toSet()));
        OperationalContextCatalogNestedFields.removeUnknown(OperationalContextCatalogEntityType.INTEGRATION, payload);
        var participants = castMap(payload.get("participants"));
        var source = castMap(participants.get("source"));
        assertFalse(source.containsKey("repositories"));
        assertFalse(source.containsKey("unusedScope"));
        assertFalse(castMap(((List<?>) participants.get("targets")).get(0)).containsKey("debugNotes"));
    }

    private static Map<String, Object> object(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
