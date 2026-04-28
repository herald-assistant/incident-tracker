package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record OperationalContextEvidenceView(
        List<SystemItem> systems,
        List<IntegrationItem> integrations,
        List<ProcessItem> processes,
        List<RepositoryItem> repositories,
        List<BoundedContextItem> boundedContexts,
        List<TeamItem> teams,
        List<GlossaryTermItem> glossaryTerms,
        List<HandoffRuleItem> handoffRules
) {

    public static final AnalysisEvidenceReference EVIDENCE_REFERENCE =
            new AnalysisEvidenceReference("operational-context", "matched-context");

    static final String ATTRIBUTE_SYSTEM_ID = "systemId";
    static final String ATTRIBUTE_INTEGRATION_ID = "integrationId";
    static final String ATTRIBUTE_PROCESS_ID = "processId";
    static final String ATTRIBUTE_REPOSITORY_ID = "repositoryId";
    static final String ATTRIBUTE_BOUNDED_CONTEXT_ID = "boundedContextId";
    static final String ATTRIBUTE_TEAM_ID = "teamId";
    static final String ATTRIBUTE_TERM_ID = "termId";
    static final String ATTRIBUTE_RULE_ID = "ruleId";
    static final String ATTRIBUTE_NAME = "name";
    static final String ATTRIBUTE_OWNER_TEAM_ID = "ownerTeamId";
    static final String ATTRIBUTE_PARTNER_TEAM_IDS = "partnerTeamIds";
    static final String ATTRIBUTE_EXTERNAL_OWNER = "externalOwner";
    static final String ATTRIBUTE_PROCESS_IDS = "processIds";
    static final String ATTRIBUTE_CONTEXT_IDS = "contextIds";
    static final String ATTRIBUTE_REPO_IDS = "repoIds";
    static final String ATTRIBUTE_CODE_SEARCH_REPO_IDS = "codeSearchRepoIds";
    static final String ATTRIBUTE_CODE_SEARCH_PROJECTS = "codeSearchProjects";
    static final String ATTRIBUTE_MATCHED_BY = "matchedBy";
    static final String ATTRIBUTE_FROM = "from";
    static final String ATTRIBUTE_TO = "to";
    static final String ATTRIBUTE_PROTOCOL = "protocol";
    static final String ATTRIBUTE_HANDOFF_TARGET = "handoffTarget";
    static final String ATTRIBUTE_SYSTEM_IDS = "systemIds";
    static final String ATTRIBUTE_EXTERNAL_SYSTEM_IDS = "externalSystemIds";
    static final String ATTRIBUTE_COMPLETION_SIGNALS = "completionSignals";
    static final String ATTRIBUTE_PROJECT = "project";
    static final String ATTRIBUTE_GROUP = "group";
    static final String ATTRIBUTE_MODULE_IDS = "moduleIds";
    static final String ATTRIBUTE_SOURCE_PATHS = "sourcePaths";
    static final String ATTRIBUTE_SOURCE_PACKAGES = "sourcePackages";
    static final String ATTRIBUTE_CLASS_HINTS = "classHints";
    static final String ATTRIBUTE_TERMS = "terms";
    static final String ATTRIBUTE_INTEGRATION_IDS = "integrationIds";
    static final String ATTRIBUTE_DEFINITION = "definition";
    static final String ATTRIBUTE_TYPICAL_EVIDENCE_SIGNALS = "typicalEvidenceSignals";
    static final String ATTRIBUTE_CANONICAL_REFERENCES = "canonicalReferences";
    static final String ATTRIBUTE_ROUTE_TO = "routeTo";
    static final String ATTRIBUTE_REQUIRED_EVIDENCE = "requiredEvidence";
    static final String ATTRIBUTE_EXPECTED_FIRST_ACTION = "expectedFirstAction";
    static final String ATTRIBUTE_PARTNER_TEAMS = "partnerTeams";

    public OperationalContextEvidenceView {
        systems = immutable(systems);
        integrations = immutable(integrations);
        processes = immutable(processes);
        repositories = immutable(repositories);
        boundedContexts = immutable(boundedContexts);
        teams = immutable(teams);
        glossaryTerms = immutable(glossaryTerms);
        handoffRules = immutable(handoffRules);
    }

    public static OperationalContextEvidenceView from(AnalysisContext context) {
        return from(context.evidenceSections());
    }

    public static OperationalContextEvidenceView from(List<AnalysisEvidenceSection> evidenceSections) {
        return evidenceSections.stream()
                .filter(OperationalContextEvidenceView::matches)
                .findFirst()
                .map(OperationalContextEvidenceView::from)
                .orElseGet(OperationalContextEvidenceView::empty);
    }

    public static OperationalContextEvidenceView from(AnalysisEvidenceSection section) {
        if (!matches(section)) {
            return empty();
        }

        var systems = new ArrayList<SystemItem>();
        var integrations = new ArrayList<IntegrationItem>();
        var processes = new ArrayList<ProcessItem>();
        var repositories = new ArrayList<RepositoryItem>();
        var boundedContexts = new ArrayList<BoundedContextItem>();
        var teams = new ArrayList<TeamItem>();
        var glossaryTerms = new ArrayList<GlossaryTermItem>();
        var handoffRules = new ArrayList<HandoffRuleItem>();

        for (var item : section.items()) {
            var attributes = AnalysisEvidenceAttributes.byName(item.attributes());

            if (hasAttribute(attributes, ATTRIBUTE_SYSTEM_ID)) {
                systems.add(new SystemItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYSTEM_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_OWNER_TEAM_ID),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PARTNER_TEAM_IDS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXTERNAL_OWNER),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROCESS_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTEXT_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REPO_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CODE_SEARCH_REPO_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CODE_SEARCH_PROJECTS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SOURCE_PACKAGES)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CLASS_HINTS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_INTEGRATION_ID)) {
                integrations.add(new IntegrationItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_INTEGRATION_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_FROM),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TO),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_OWNER_TEAM_ID),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PARTNER_TEAM_IDS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXTERNAL_OWNER),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROTOCOL),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_HANDOFF_TARGET),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_PROCESS_ID)) {
                processes.add(new ProcessItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROCESS_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_OWNER_TEAM_ID),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PARTNER_TEAM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYSTEM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXTERNAL_SYSTEM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_COMPLETION_SIGNALS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_REPOSITORY_ID)) {
                repositories.add(new RepositoryItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REPOSITORY_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROJECT),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_GROUP),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_OWNER_TEAM_ID),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYSTEM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROCESS_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTEXT_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MODULE_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SOURCE_PATHS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SOURCE_PACKAGES)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CLASS_HINTS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_BOUNDED_CONTEXT_ID)) {
                boundedContexts.add(new BoundedContextItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_BOUNDED_CONTEXT_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAME),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_OWNER_TEAM_ID),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYSTEM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REPO_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROCESS_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TERMS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_TEAM_ID)) {
                teams.add(new TeamItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TEAM_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_NAME),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_SYSTEM_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REPO_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PROCESS_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CONTEXT_IDS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_INTEGRATION_IDS)),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_HANDOFF_TARGET),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_TERM_ID)) {
                glossaryTerms.add(new GlossaryTermItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TERM_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_DEFINITION),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_TYPICAL_EVIDENCE_SIGNALS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_CANONICAL_REFERENCES)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
                continue;
            }

            if (hasAttribute(attributes, ATTRIBUTE_RULE_ID)) {
                handoffRules.add(new HandoffRuleItem(
                        item.title(),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_RULE_ID),
                        AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_ROUTE_TO),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_REQUIRED_EVIDENCE)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_EXPECTED_FIRST_ACTION)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_PARTNER_TEAMS)),
                        splitValues(AnalysisEvidenceAttributes.text(attributes, ATTRIBUTE_MATCHED_BY))
                ));
            }
        }

        return new OperationalContextEvidenceView(
                systems,
                integrations,
                processes,
                repositories,
                boundedContexts,
                teams,
                glossaryTerms,
                handoffRules
        );
    }

    public static OperationalContextEvidenceView empty() {
        return new OperationalContextEvidenceView(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public static boolean matches(AnalysisEvidenceSection section) {
        return EVIDENCE_REFERENCE.provider().equals(section.provider())
                && EVIDENCE_REFERENCE.category().equals(section.category());
    }

    public boolean isEmpty() {
        return systems.isEmpty()
                && integrations.isEmpty()
                && processes.isEmpty()
                && repositories.isEmpty()
                && boundedContexts.isEmpty()
                && teams.isEmpty()
                && glossaryTerms.isEmpty()
                && handoffRules.isEmpty();
    }

    private static boolean hasAttribute(java.util.Map<String, String> attributes, String key) {
        return StringUtils.hasText(AnalysisEvidenceAttributes.text(attributes, key));
    }

    private static List<String> splitValues(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return List.of();
        }

        return Arrays.stream(rawValue.split(";"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    public record SystemItem(
            String title,
            String systemId,
            String name,
            String ownerTeamId,
            List<String> partnerTeamIds,
            String externalOwner,
            List<String> processIds,
            List<String> contextIds,
            List<String> repoIds,
            List<String> codeSearchRepoIds,
            List<String> codeSearchProjects,
            List<String> sourcePackages,
            List<String> classHints,
            List<String> matchedBy
    ) {
        public SystemItem {
            partnerTeamIds = immutable(partnerTeamIds);
            processIds = immutable(processIds);
            contextIds = immutable(contextIds);
            repoIds = immutable(repoIds);
            codeSearchRepoIds = immutable(codeSearchRepoIds);
            codeSearchProjects = immutable(codeSearchProjects);
            sourcePackages = immutable(sourcePackages);
            classHints = immutable(classHints);
            matchedBy = immutable(matchedBy);
        }
    }

    public record IntegrationItem(
            String title,
            String integrationId,
            String name,
            String from,
            String to,
            String ownerTeamId,
            List<String> partnerTeamIds,
            String externalOwner,
            String protocol,
            String handoffTarget,
            List<String> matchedBy
    ) {
        public IntegrationItem {
            partnerTeamIds = immutable(partnerTeamIds);
            matchedBy = immutable(matchedBy);
        }
    }

    public record ProcessItem(
            String title,
            String processId,
            String name,
            String ownerTeamId,
            List<String> partnerTeamIds,
            List<String> systemIds,
            List<String> externalSystemIds,
            List<String> completionSignals,
            List<String> matchedBy
    ) {
        public ProcessItem {
            partnerTeamIds = immutable(partnerTeamIds);
            systemIds = immutable(systemIds);
            externalSystemIds = immutable(externalSystemIds);
            completionSignals = immutable(completionSignals);
            matchedBy = immutable(matchedBy);
        }
    }

    public record RepositoryItem(
            String title,
            String repositoryId,
            String project,
            String group,
            String ownerTeamId,
            List<String> systemIds,
            List<String> processIds,
            List<String> contextIds,
            List<String> moduleIds,
            List<String> sourcePaths,
            List<String> sourcePackages,
            List<String> classHints,
            List<String> matchedBy
    ) {
        public RepositoryItem {
            systemIds = immutable(systemIds);
            processIds = immutable(processIds);
            contextIds = immutable(contextIds);
            moduleIds = immutable(moduleIds);
            sourcePaths = immutable(sourcePaths);
            sourcePackages = immutable(sourcePackages);
            classHints = immutable(classHints);
            matchedBy = immutable(matchedBy);
        }
    }

    public record BoundedContextItem(
            String title,
            String boundedContextId,
            String name,
            String ownerTeamId,
            List<String> systemIds,
            List<String> repoIds,
            List<String> processIds,
            List<String> terms,
            List<String> matchedBy
    ) {
        public BoundedContextItem {
            systemIds = immutable(systemIds);
            repoIds = immutable(repoIds);
            processIds = immutable(processIds);
            terms = immutable(terms);
            matchedBy = immutable(matchedBy);
        }
    }

    public record TeamItem(
            String title,
            String teamId,
            String name,
            List<String> systemIds,
            List<String> repoIds,
            List<String> processIds,
            List<String> contextIds,
            List<String> integrationIds,
            String handoffTarget,
            List<String> matchedBy
    ) {
        public TeamItem {
            systemIds = immutable(systemIds);
            repoIds = immutable(repoIds);
            processIds = immutable(processIds);
            contextIds = immutable(contextIds);
            integrationIds = immutable(integrationIds);
            matchedBy = immutable(matchedBy);
        }
    }

    public record GlossaryTermItem(
            String title,
            String termId,
            String definition,
            List<String> typicalEvidenceSignals,
            List<String> canonicalReferences,
            List<String> matchedBy
    ) {
        public GlossaryTermItem {
            typicalEvidenceSignals = immutable(typicalEvidenceSignals);
            canonicalReferences = immutable(canonicalReferences);
            matchedBy = immutable(matchedBy);
        }
    }

    public record HandoffRuleItem(
            String title,
            String ruleId,
            String routeTo,
            List<String> requiredEvidence,
            List<String> expectedFirstAction,
            List<String> partnerTeams,
            List<String> matchedBy
    ) {
        public HandoffRuleItem {
            requiredEvidence = immutable(requiredEvidence);
            expectedFirstAction = immutable(expectedFirstAction);
            partnerTeams = immutable(partnerTeams);
            matchedBy = immutable(matchedBy);
        }
    }
}
