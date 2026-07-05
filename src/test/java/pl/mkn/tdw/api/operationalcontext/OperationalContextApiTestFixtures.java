package pl.mkn.tdw.api.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OperationalContextApiTestFixtures {

    private OperationalContextApiTestFixtures() {
    }

    static OperationalContextPort port(OperationalContextCatalog catalog) {
        return query -> catalog;
    }

    static OperationalContextCatalog emptyCatalog() {
        return OperationalContextCatalog.empty();
    }

    static OperationalContextCatalog typicalCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "team-a",
                        "name", "Team A",
                        "purpose", "Owns customer consent analysis context.",
                        "references", map(
                                "systems", List.of("crm-consent-service"),
                                "repositories", List.of("crm-consent-repo"),
                                "processes", List.of("customer-consent-capture-process"),
                                "boundedContexts", List.of("customer-consent-context"),
                                "integrations", List.of("customer-consent-registry-handoff")
                        )
                )),
                List.of(map(
                        "id", "customer-consent-capture-process",
                        "name", "Customer Consent Capture",
                        "summary", "Business process for capturing customer consent.",
                        "participants", map(
                                "primarySystems", List.of("crm-consent-service"),
                                "externalSystems", List.of("consent-registry")
                        ),
                        "references", map(
                                "systems", List.of("crm-consent-service"),
                                "repositories", List.of("crm-consent-repo"),
                                "boundedContexts", List.of("customer-consent-context"),
                                "teams", List.of("team-a")
                        ),
                        "processSteps", List.of(map(
                                "id", "capture",
                                "name", "Capture customer consent",
                                "summary", "Validates customer consent and passes it to registry handoff."
                        )),
                        "processBoundary", map("successArtifacts", List.of("customer consent captured"))
                )),
                List.of(
                        map(
                                "id", "crm-consent-service",
                                "name", "CRM Consent Service",
                                "kind", "internal-application",
                                "purpose", "Owns customer consent capture decisions.",
                                "aliases", List.of("consents"),
                                "references", map(
                                        "repositories", List.of("crm-consent-repo"),
                                        "processes", List.of("customer-consent-capture-process"),
                                        "boundedContexts", List.of("customer-consent-context"),
                                        "teams", List.of("team-a")
                                ),
                                "ownership", ownership(List.of("team-a"), null, "high"),
                                "matchSignals", map("exact", map("markers", List.of("crm-consent-service")))
                        ),
                        map(
                                "id", "consent-registry",
                                "name", "Consent Registry",
                                "kind", "external-system",
                                "purpose", "Receives captured customer consent records."
                        )
                ),
                List.of(map(
                        "id", "customer-consent-registry-handoff",
                        "name", "Customer Consent Registry Handoff",
                        "category", "external-handoff",
                        "summary", "Business handoff from CRM consent service to consent registry.",
                        "integrationStyle", "synchronous-request",
                        "flowDirection", "outbound",
                        "participants", map(
                                "source", map("system", "crm-consent-service"),
                                "targets", List.of(map("system", "consent-registry")),
                                "finalTargets", List.of(map("system", "consent-registry"))
                        ),
                        "references", map(
                                "processes", List.of("customer-consent-capture-process"),
                                "boundedContexts", List.of("customer-consent-context"),
                                "teams", List.of("team-a")
                        ),
                        "matchSignals", map("strong", map("terms", List.of("consent-registry-handoff")))
                )),
                List.of(map(
                        "id", "crm-consent-repo",
                        "name", "CRM Consent Repo",
                        "summary", "Repository for customer consent service source.",
                        "git", map(
                                "provider", "gitlab",
                                "group", "Group",
                                "project", "crm-consent-service",
                                "projectPath", "Group/crm-consent-service",
                                "defaultBranch", "main"
                        ),
                        "references", map(
                                "systems", List.of("crm-consent-service"),
                                "processes", List.of("customer-consent-capture-process"),
                                "boundedContexts", List.of("customer-consent-context"),
                                "teams", List.of("team-a")
                        )
                )),
                List.of(
                        map(
                                "id", "crm-consent-service-scope",
                                "name", "CRM Consent Service Scope",
                                "scopeType", "system",
                                "lifecycleStatus", "active",
                                "summary", "Repository scope for the CRM consent service.",
                                "target", map("type", "system", "id", "crm-consent-service"),
                                "useFor", List.of("business-analysis", "test-scenario-design"),
                                "repositories", List.of(map(
                                        "repoId", "crm-consent-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "reason", "Main repository for customer consent service analysis.",
                                        "readFor", List.of("business logic")
                                )),
                                "limitations", List.of("Consent registry internals are outside this catalog.")
                        ),
                        map(
                                "id", "customer-consent-process-scope",
                                "name", "Customer Consent Process Scope",
                                "scopeType", "process",
                                "lifecycleStatus", "active",
                                "summary", "Repository scope for the customer consent capture process.",
                                "target", map("type", "process", "id", "customer-consent-capture-process"),
                                "useFor", List.of("business-analysis"),
                                "repositories", List.of(map(
                                        "repoId", "crm-consent-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "reason", "Main repository for customer consent capture analysis.",
                                        "readFor", List.of("process rules")
                                ))
                        )
                ),
                List.of(map(
                        "id", "customer-consent-context",
                        "name", "Customer Consent Context",
                        "summary", "Bounded context for customer consent decisions.",
                        "references", map(
                                "systems", List.of("crm-consent-service"),
                                "repositories", List.of("crm-consent-repo"),
                                "processes", List.of("customer-consent-capture-process"),
                                "terms", List.of("customer-consent"),
                                "teams", List.of("team-a")
                        ),
                        "ownership", ownership(List.of("team-a"), null, "high"),
                        "matchSignals", map("exact", map("terms", List.of("customer-consent")))
                )),
                List.of(new OperationalContextGlossaryTerm(
                        "customer-consent",
                        "Customer Consent",
                        "domain-term",
                        "Consent record captured for a customer.",
                        List.of("customer-consent-capture-process"),
                        List.of(),
                        List.of("consent", "customer consent"),
                        List.of("customer-consent-context"),
                        List.of("opt-in"),
                        List.of()
                )),
                List.of(new OperationalContextHandoffRule(
                        "handoff-team-a",
                        "Route customer consent questions to Team A",
                        List.of("crm-consent-service", "customer-consent-capture-process"),
                        List.of(),
                        List.of("business scenario"),
                        List.of("Open customer consent context"),
                        List.of("Keep language business-oriented.")
                )),
                List.of(new OperationalContextOpenQuestion(
                        "question-1",
                        "systems.yml",
                        "system",
                        "crm-consent-service",
                        "Clarify consent registry SLA ownership.",
                        "medium",
                        "open"
                )),
                "index"
        );
    }

    static OperationalContextCatalog brokenCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "core-team",
                        "name", "Core Team",
                        "references", map("systems", List.of(), "repositories", List.of())
                )),
                List.of(),
                List.of(
                        map(
                                "id", "app-core",
                                "name", "App Core",
                                "kind", "internal-application",
                                "references", map("repositories", List.of("missing-repo")),
                                "matchSignals", map("exact", map("markers", List.of("shared-service")))
                        ),
                        map(
                                "id", "crm-customer-profile",
                                "name", "CRM Customer Profile",
                                "kind", "internal-application",
                                "matchSignals", map("exact", map("markers", List.of("shared-service")))
                        )
                ),
                List.of(),
                List.of(map(
                        "id", "broken-repo",
                        "name", "Broken Repo",
                        "git", map("group", "Group", "project", "broken", "projectPath", "Group/broken"),
                        "references", map("systems", List.of("missing-system"))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    static Map<String, Object> map(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private static Map<String, Object> ownership(List<String> ownerTeamIds, String ownerLabel, String confidence) {
        return map(
                "ownerTeamIds", ownerTeamIds,
                "ownerLabel", ownerLabel,
                "ownershipStatus", "explicit",
                "confidence", confidence,
                "source", "test"
        );
    }
}
