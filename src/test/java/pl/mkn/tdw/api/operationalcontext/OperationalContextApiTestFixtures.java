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
                        "purpose", "Owns agreement analysis context.",
                        "references", map(
                                "systems", List.of("agreement-service"),
                                "repositories", List.of("agreement-repo"),
                                "processes", List.of("agreement-submit-process"),
                                "boundedContexts", List.of("agreement-context"),
                                "integrations", List.of("agreement-partner-handoff")
                        ),
                        "handoffHints", map(
                                "defaultRouteLabel", "Team A",
                                "requiredEvidence", List.of("business scenario")
                        )
                )),
                List.of(map(
                        "id", "agreement-submit-process",
                        "name", "Agreement Submit",
                        "summary", "Business process for submitting an agreement.",
                        "participants", map(
                                "primarySystems", List.of("agreement-service"),
                                "externalSystems", List.of("partner-system")
                        ),
                        "references", map(
                                "systems", List.of("agreement-service"),
                                "repositories", List.of("agreement-repo"),
                                "boundedContexts", List.of("agreement-context"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "processSteps", List.of(map(
                                "id", "submit",
                                "name", "Submit agreement",
                                "summary", "Validates and passes the agreement to partner handoff."
                        )),
                        "processBoundary", map("successArtifacts", List.of("agreement accepted")),
                        "handoffHints", map(
                                "defaultRouteLabel", "Team A",
                                "requiredEvidence", List.of("business scenario")
                        )
                )),
                List.of(
                        map(
                                "id", "agreement-service",
                                "name", "Agreement Service",
                                "kind", "internal-application",
                                "purpose", "Owns agreement submission decisions.",
                                "aliases", List.of("agreements"),
                                "references", map(
                                        "repositories", List.of("agreement-repo"),
                                        "processes", List.of("agreement-submit-process"),
                                        "boundedContexts", List.of("agreement-context"),
                                        "teams", List.of("team-a")
                                ),
                                "responsibilities", List.of(map("teamId", "team-a")),
                                "matchSignals", map("exact", map("markers", List.of("agreement-service"))),
                                "handoffHints", map(
                                        "defaultRouteLabel", "Team A",
                                        "requiredEvidence", List.of("business scenario")
                                )
                        ),
                        map(
                                "id", "partner-system",
                                "name", "Partner System",
                                "kind", "external-system",
                                "purpose", "Receives accepted agreements.",
                                "handoffHints", map("defaultRouteLabel", "Partner")
                        )
                ),
                List.of(map(
                        "id", "agreement-partner-handoff",
                        "name", "Agreement Partner Handoff",
                        "category", "external-handoff",
                        "summary", "Business handoff from agreement service to partner system.",
                        "integrationStyle", "synchronous-request",
                        "flowDirection", "outbound",
                        "participants", map(
                                "source", map("system", "agreement-service"),
                                "targets", List.of(map("system", "partner-system")),
                                "finalTargets", List.of(map("system", "partner-system"))
                        ),
                        "references", map(
                                "processes", List.of("agreement-submit-process"),
                                "boundedContexts", List.of("agreement-context"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "matchSignals", map("strong", map("terms", List.of("partner-handoff"))),
                        "handoffHints", map(
                                "defaultRouteLabel", "Team A",
                                "partnerTeamIds", List.of("team-a"),
                                "requiredEvidence", List.of("business scenario")
                        )
                )),
                List.of(map(
                        "id", "agreement-repo",
                        "name", "Agreement Repo",
                        "summary", "Repository for agreement service source.",
                        "git", map(
                                "provider", "gitlab",
                                "group", "Group",
                                "project", "agreement-service",
                                "projectPath", "Group/agreement-service",
                                "defaultBranch", "main"
                        ),
                        "references", map(
                                "systems", List.of("agreement-service"),
                                "processes", List.of("agreement-submit-process"),
                                "boundedContexts", List.of("agreement-context"),
                                "teams", List.of("team-a")
                        ),
                        "handoffHints", map(
                                "defaultRouteLabel", "Team A",
                                "requiredEvidence", List.of("project path")
                        )
                )),
                List.of(
                        map(
                                "id", "agreement-service-scope",
                                "name", "Agreement Service Scope",
                                "scopeType", "system",
                                "lifecycleStatus", "active",
                                "summary", "Repository scope for the agreement service.",
                                "target", map("type", "system", "id", "agreement-service"),
                                "useFor", List.of("business-analysis", "test-scenario-design"),
                                "repositories", List.of(map(
                                        "repoId", "agreement-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "reason", "Main repository for agreement service analysis.",
                                        "readFor", List.of("business logic")
                                )),
                                "limitations", List.of("Partner internals are outside this catalog.")
                        ),
                        map(
                                "id", "agreement-process-scope",
                                "name", "Agreement Process Scope",
                                "scopeType", "process",
                                "lifecycleStatus", "active",
                                "summary", "Repository scope for the agreement submit process.",
                                "target", map("type", "process", "id", "agreement-submit-process"),
                                "useFor", List.of("business-analysis"),
                                "repositories", List.of(map(
                                        "repoId", "agreement-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "reason", "Main repository for agreement submit analysis.",
                                        "readFor", List.of("process rules")
                                ))
                        )
                ),
                List.of(map(
                        "id", "agreement-context",
                        "name", "Agreement Context",
                        "summary", "Bounded context for agreement decisions.",
                        "references", map(
                                "systems", List.of("agreement-service"),
                                "repositories", List.of("agreement-repo"),
                                "processes", List.of("agreement-submit-process"),
                                "terms", List.of("agreement"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "matchSignals", map("exact", map("terms", List.of("agreement")))
                )),
                List.of(new OperationalContextGlossaryTerm(
                        "agreement",
                        "Agreement",
                        "domain-term",
                        "Business object submitted by a user.",
                        List.of("agreement-submit-process"),
                        List.of(),
                        List.of("agreement"),
                        List.of("agreement-context"),
                        List.of("contract"),
                        List.of()
                )),
                List.of(new OperationalContextHandoffRule(
                        "handoff-team-a",
                        "Route agreement questions to Team A",
                        "team-a",
                        List.of("agreement-service", "agreement-submit-process"),
                        List.of(),
                        List.of("business scenario"),
                        List.of("Open agreement context"),
                        List.of("team-a"),
                        List.of("Keep language business-oriented.")
                )),
                List.of(new OperationalContextOpenQuestion(
                        "question-1",
                        "systems.yml",
                        "system",
                        "agreement-service",
                        "Clarify partner SLA ownership.",
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
                                "responsibilities", List.of(map("teamId", "core-team")),
                                "matchSignals", map("exact", map("markers", List.of("shared-service")))
                        ),
                        map(
                                "id", "catalog-core",
                                "name", "Catalog Core",
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
}
