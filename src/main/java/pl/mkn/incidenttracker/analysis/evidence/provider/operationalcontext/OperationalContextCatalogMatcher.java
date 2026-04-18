package pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalog.GlossaryTerm;
import pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextCatalog.HandoffRule;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.normalize;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMaps.textList;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.anyOverlap;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.containsAnyId;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.containsId;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.genericSignals;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.matchedIds;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textAny;
import static pl.mkn.incidenttracker.analysis.evidence.provider.operationalcontext.OperationalContextMatchingSupport.textListAny;

@Component
@RequiredArgsConstructor
public class OperationalContextCatalogMatcher {

    private final OperationalContextProperties properties;

    public OperationalContextMatchBundle match(
            OperationalContextCatalog catalog,
            OperationalContextIncidentSignals signals
    ) {
        var systemMatches = matchEntries(
                catalog.systems(),
                entry -> scoreSystem(entry, signals),
                properties.getMaxItemsPerType()
        );
        var integrationMatches = matchEntries(
                catalog.integrations(),
                entry -> scoreIntegration(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var repositoryMatches = matchEntries(
                catalog.repositories(),
                entry -> scoreRepository(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var processMatches = matchEntries(
                catalog.processes(),
                entry -> scoreProcess(entry, signals, systemMatches),
                properties.getMaxItemsPerType()
        );
        var boundedContextMatches = matchEntries(
                catalog.boundedContexts(),
                entry -> scoreBoundedContext(entry, signals, systemMatches, processMatches, repositoryMatches),
                properties.getMaxItemsPerType()
        );
        var teamMatches = matchEntries(
                catalog.teams(),
                entry -> scoreTeam(
                        entry,
                        systemMatches,
                        processMatches,
                        repositoryMatches,
                        boundedContextMatches,
                        integrationMatches
                ),
                properties.getMaxItemsPerType()
        );
        var glossaryMatches = matchGlossaryTerms(catalog.glossaryTerms(), signals);
        var handoffMatches = matchHandoffRules(
                catalog.handoffRules(),
                signals,
                integrationMatches,
                boundedContextMatches
        );

        return new OperationalContextMatchBundle(
                systemMatches,
                integrationMatches,
                processMatches,
                repositoryMatches,
                boundedContextMatches,
                teamMatches,
                glossaryMatches,
                handoffMatches
        );
    }

    private List<OperationalContextMatchedEntry<Map<String, Object>>> matchEntries(
            List<Map<String, Object>> entries,
            Function<Map<String, Object>, OperationalContextMatchScore> scorer,
            int limit
    ) {
        return entries.stream()
                .map(entry -> new OperationalContextMatchedEntry<>(entry, scorer.apply(entry)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<Map<String, Object>> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> text(match.entry(), "id"), Comparator.nullsLast(String::compareTo)))
                .limit(limit)
                .toList();
    }

    private List<OperationalContextMatchedEntry<GlossaryTerm>> matchGlossaryTerms(
            List<GlossaryTerm> terms,
            OperationalContextIncidentSignals signals
    ) {
        return terms.stream()
                .map(term -> new OperationalContextMatchedEntry<>(term, scoreGlossaryTerm(term, signals)))
                .filter(match -> match.score().score() > 0)
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<GlossaryTerm> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxGlossaryTerms())
                .toList();
    }

    private List<OperationalContextMatchedEntry<HandoffRule>> matchHandoffRules(
            List<HandoffRule> rules,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> integrationMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> boundedContextMatches
    ) {
        var selectedRuleIds = new LinkedHashSet<String>();
        if (signals.containsAny("timeout", "soapfault", "connection reset", "read timed out")
                && !integrationMatches.isEmpty()) {
            selectedRuleIds.add("integration-external-sync-failure");
        }
        if (signals.containsAny("queue", "topic", "routing key", "consumer lag", "serialization")
                || signals.matchesAttributeNames("queuename", "topicname")) {
            selectedRuleIds.add("integration-async-messaging-stall");
        }
        if (signals.containsAny("jdbc", "sql", "oracle", "deadlock", "schema", "lock")) {
            selectedRuleIds.add("database-state-or-schema-issue");
        }
        if (signals.containsAny("namespace", "certificate", "host resolution", "deployment", "unavailable")) {
            selectedRuleIds.add("platform-or-environment-issue");
        }
        if (!boundedContextMatches.isEmpty()
                && signals.containsAny("contract", "payload", "state", "mismatch", "semantic")) {
            selectedRuleIds.add("bounded-context-boundary-mismatch");
        }
        if (selectedRuleIds.isEmpty()) {
            selectedRuleIds.add("retain-with-current-owner");
        }

        return rules.stream()
                .filter(rule -> selectedRuleIds.contains(rule.id()))
                .map(rule -> new OperationalContextMatchedEntry<>(rule, scoreHandoffRule(rule, signals)))
                .sorted(Comparator
                        .comparingInt((OperationalContextMatchedEntry<HandoffRule> match) -> match.score().score())
                        .reversed()
                        .thenComparing(match -> match.entry().id()))
                .limit(properties.getMaxHandoffRules())
                .toList();
    }

    private OperationalContextMatchScore scoreSystem(
            Map<String, Object> system,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, system, "system");
        addSignalMatches(score, signals, genericSignals(system), 10, 6, "signal");
        addSignalMatches(score, signals, textListAny(system, "processes", "domainContext.processes"), 4, 2, "process");
        addSignalMatches(score, signals, textListAny(system, "contexts", "domainContext.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textListAny(system, "repos", "repositories.primary"), 6, 4, "repo");
        addSignalMatches(score, signals, textListAny(system, "dependsOn", "dependencies.externalSystemIds"), 3, 2, "dependency");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.serviceNames"), 12, 8, "serviceName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.containerNames"), 12, 8, "containerName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.applicationNames"), 10, 6, "applicationName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.projectNames"), 10, 6, "projectName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.packagePrefixes"), 9, 5, "packagePrefix");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.endpointPrefixes"), 9, 5, "endpointPrefix");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.hostPatterns"), 9, 5, "hostPattern");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.queueNames"), 9, 5, "queueName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.topicNames"), 9, 5, "topicName");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.eventTypes"), 7, 4, "eventType");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.databaseSchemas"), 7, 4, "databaseSchema");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.traceSpans"), 8, 5, "traceSpan");
        addSignalMatches(score, signals, textList(system, "runtimeFingerprints.logMarkers"), 6, 3, "logMarker");
        addSignalMatches(score, signals, textList(system, "repositories.primary"), 6, 4, "repository");
        addSignalMatches(score, signals, textList(system, "repositories.secondary"), 4, 2, "repository");
        addSignalMatches(score, signals, textList(system, "repositories.backendModules"), 5, 3, "backendModule");
        addSignalMatches(score, signals, textList(system, "repositories.frontendModules"), 5, 3, "frontendModule");
        addSignalMatches(score, signals, textList(system, "repositories.packageRoots"), 7, 4, "packageRoot");
        addSignalMatches(score, signals, textList(system, "dependencies.upstreamSystemIds"), 3, 2, "upstreamSystem");
        addSignalMatches(score, signals, textList(system, "dependencies.downstreamSystemIds"), 3, 2, "downstreamSystem");
        addSignalMatches(score, signals, textList(system, "dependencies.externalSystemIds"), 3, 2, "externalSystem");
        return score;
    }

    private OperationalContextMatchScore scoreIntegration(
            Map<String, Object> integration,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, integration, "integration");
        addSignalMatches(score, signals, genericSignals(integration), 11, 7, "signal");
        addSignalMatches(
                score,
                signals,
                textListAny(integration, "processes", "topology.sourceProcessIds", "topology.targetProcessIds"),
                4,
                2,
                "process"
        );
        addSignalMatches(score, signals, textListAny(integration, "contexts", "topology.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(integration, "contract.operations"), 6, 3, "operation");
        addSignalMatches(score, signals, textList(integration, "contract.endpointPatterns"), 11, 7, "endpoint");
        addSignalMatches(score, signals, textList(integration, "contract.hostPatterns"), 11, 7, "host");
        addSignalMatches(score, signals, textList(integration, "contract.soapActions"), 9, 5, "soapAction");
        addSignalMatches(score, signals, textList(integration, "contract.queueNames"), 9, 5, "queue");
        addSignalMatches(score, signals, textList(integration, "contract.topicNames"), 9, 5, "topic");
        addSignalMatches(score, signals, textList(integration, "contract.exchangeNames"), 8, 4, "exchange");
        addSignalMatches(score, signals, textList(integration, "contract.routingKeys"), 8, 4, "routingKey");
        addSignalMatches(score, signals, textList(integration, "contract.databaseSchemas"), 8, 4, "databaseSchema");
        addSignalMatches(score, signals, textList(integration, "contract.filePatterns"), 8, 4, "filePattern");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.serviceNames"), 8, 5, "serviceName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.containerNames"), 8, 5, "containerName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.applicationNames"), 7, 4, "applicationName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.endpointPrefixes"), 9, 5, "endpointPrefix");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.hostPatterns"), 9, 5, "hostPattern");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.queueNames"), 9, 5, "queueName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.topicNames"), 9, 5, "topicName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.exchangeNames"), 8, 4, "exchangeName");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.routingKeys"), 8, 4, "routingKey");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.databaseSchemas"), 8, 4, "databaseSchema");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.traceSpans"), 8, 4, "traceSpan");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.logMarkers"), 6, 3, "logMarker");
        addSignalMatches(score, signals, textList(integration, "runtimeFingerprints.errorMarkers"), 8, 5, "errorMarker");
        addSignalMatches(score, signals, textList(integration, "failureModes.commonSymptoms"), 5, 3, "symptom");
        addSignalMatches(score, signals, textList(integration, "failureModes.retriableIndicators"), 6, 3, "retriableIndicator");
        addSignalMatches(score, signals, textList(integration, "failureModes.nonRetriableIndicators"), 6, 3, "nonRetriableIndicator");
        addSignalMatches(score, signals, textList(integration, "failureModes.timeoutIndicators"), 7, 4, "timeoutIndicator");
        addSignalMatches(score, signals, textList(integration, "failureModes.dataMismatchIndicators"), 7, 4, "dataMismatchIndicator");

        var matchedSystemIds = matchedIds(systemMatches);
        if (containsAnyId(
                matchedSystemIds,
                textAny(integration, "from", "topology.sourceSystemId"),
                textAny(integration, "to", "topology.targetSystemId")
        )) {
            score.add(10, "sourceOrTargetSystemMatches");
        }
        if (anyOverlap(textList(integration, "topology.intermediarySystemIds"), matchedSystemIds)) {
            score.add(6, "intermediarySystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreRepository(
            Map<String, Object> repository,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, repository, "repository");
        addSignalMatches(score, signals, genericSignals(repository), 11, 7, "signal");
        addSignalMatch(score, signals, text(repository, "project"), 12, 8, "project");
        addSignalMatch(score, signals, text(repository, "group"), 5, 2, "group");
        addSignalMatches(score, signals, textListAny(repository, "systems", "topology.systemIds"), 6, 3, "system");
        addSignalMatches(score, signals, textListAny(repository, "processes", "topology.processIds"), 5, 3, "process");
        addSignalMatches(score, signals, textListAny(repository, "contexts", "topology.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textList(repository, "gitLab.projectPath"), 12, 8, "projectPath");
        addSignalMatches(score, signals, textList(repository, "gitLab.groupPath"), 6, 3, "groupPath");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.packageRoots"), 10, 6, "packageRoot");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.classNamePrefixes"), 8, 5, "classNamePrefix");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.importantPaths"), 8, 5, "importantPath");
        addSignalMatches(score, signals, textList(repository, "sourceLayout.entrypoints"), 7, 4, "entrypoint");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.serviceNames"), 8, 5, "serviceName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.containerNames"), 8, 5, "containerName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.applicationNames"), 7, 4, "applicationName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.projectNames"), 11, 7, "projectName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.endpointPrefixes"), 8, 5, "endpointPrefix");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.queueNames"), 8, 5, "queueName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.topicNames"), 8, 5, "topicName");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.databaseSchemas"), 8, 5, "databaseSchema");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.logMarkers"), 6, 3, "logMarker");
        addSignalMatches(score, signals, textList(repository, "runtimeMappings.traceSpans"), 8, 4, "traceSpan");
        addSignalMatches(score, signals, textList(repository, "sourceLookupHints.stacktraceHotspots"), 8, 5, "stacktraceHotspot");
        addSignalMatches(score, signals, textList(repository, "sourceLookupHints.likelyEntryClasses"), 7, 4, "entryClass");
        addSignalMatches(score, signals, textList(repository, "sourceLookupHints.likelyConfigFiles"), 7, 4, "configFile");
        addSignalMatches(score, signals, textList(repository, "incidentHints.likelyChangeAreas"), 7, 4, "changeArea");

        for (var module : mapList(repository, "modules")) {
            addIdentityMatches(score, signals, module, "module");
            addSignalMatches(score, signals, textListAny(module, "paths", "pathPrefixes"), 8, 5, "modulePath");
            addSignalMatches(score, signals, textListAny(module, "packages", "packageRoots"), 9, 5, "modulePackage");
            addSignalMatches(score, signals, textListAny(module, "classHints", "runtimeFingerprints.classNameHints"), 8, 5, "classHint");
            addSignalMatches(score, signals, textList(module, "packageRoots"), 9, 5, "modulePackageRoot");
            addSignalMatches(score, signals, textList(module, "pathPrefixes"), 8, 5, "modulePath");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.serviceNames"), 7, 4, "moduleServiceName");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.containerNames"), 7, 4, "moduleContainerName");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.endpointPrefixes"), 7, 4, "moduleEndpointPrefix");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.queueNames"), 7, 4, "moduleQueueName");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.topicNames"), 7, 4, "moduleTopicName");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.databaseSchemas"), 7, 4, "moduleDatabaseSchema");
            addSignalMatches(score, signals, textList(module, "runtimeFingerprints.classNameHints"), 8, 5, "classNameHint");
            addSignalMatches(score, signals, textList(module, "sourceLookupHints.stacktraceHotspots"), 7, 4, "moduleStacktraceHotspot");
            addSignalMatches(score, signals, textList(module, "sourceLookupHints.likelyEntryClasses"), 7, 4, "moduleEntryClass");
        }

        if (anyOverlap(textList(repository, "topology.systemIds"), matchedIds(systemMatches))) {
            score.add(8, "topologySystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreProcess(
            Map<String, Object> process,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, process, "process");
        addSignalMatches(score, signals, textListAny(process, "systems", "systems.internal"), 6, 3, "system");
        addSignalMatches(score, signals, textListAny(process, "externalSystems", "systems.external"), 6, 3, "externalSystem");
        addSignalMatches(score, signals, textListAny(process, "repos", "systems.repositories"), 6, 3, "repo");
        addSignalMatches(score, signals, textListAny(process, "contexts", "scope.boundedContexts"), 5, 3, "context");
        addSignalMatches(score, signals, textListAny(process, "completionSignals", "outcomes.completionSignals"), 7, 4, "completionSignal");
        addSignalMatches(score, signals, textList(process, "scope.businessDomains"), 4, 2, "businessDomain");
        addSignalMatches(score, signals, textList(process, "scope.boundedContexts"), 5, 3, "boundedContext");
        addSignalMatches(score, signals, textList(process, "entryCriteria.triggers"), 5, 3, "trigger");
        addSignalMatches(score, signals, textList(process, "entryCriteria.inboundArtifacts"), 5, 3, "inboundArtifact");
        addSignalMatches(score, signals, textList(process, "entryCriteria.initiatingSystems"), 6, 3, "initiatingSystem");
        addSignalMatches(score, signals, textList(process, "outcomes.successArtifacts"), 5, 3, "successArtifact");
        addSignalMatches(score, signals, textList(process, "outcomes.failureArtifacts"), 5, 3, "failureArtifact");
        addSignalMatches(score, signals, textList(process, "outcomes.completionSignals"), 7, 4, "completionSignal");
        addSignalMatches(score, signals, textList(process, "systems.internal"), 6, 3, "internalSystem");
        addSignalMatches(score, signals, textList(process, "systems.external"), 6, 3, "externalSystem");
        addSignalMatches(score, signals, textList(process, "systems.repositories"), 6, 3, "repository");
        addSignalMatches(score, signals, textList(process, "incidentRouting.likelyExternalSystemIds"), 5, 3, "routingExternalSystem");
        addSignalMatches(score, signals, textList(process, "observability.expectedPrimarySignals"), 7, 4, "expectedSignal");

        for (var step : mapList(process, "steps")) {
            addIdentityMatches(score, signals, step, "step");
            addSignalMatches(score, signals, genericSignals(step), 8, 5, "stepSignal");
            addSignalMatches(score, signals, textList(step, "systems"), 6, 3, "stepSystem");
            addSignalMatches(score, signals, textList(step, "participatingSystems"), 6, 3, "participatingSystem");
            addSignalMatches(score, signals, textList(step, "inboundArtifacts"), 5, 3, "stepInboundArtifact");
            addSignalMatches(score, signals, textList(step, "outboundArtifacts"), 5, 3, "stepOutboundArtifact");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.serviceNames"), 8, 5, "stepServiceName");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.containerNames"), 8, 5, "stepContainerName");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.endpointPrefixes"), 8, 5, "stepEndpointPrefix");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.queueNames"), 8, 5, "stepQueueName");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.topicNames"), 8, 5, "stepTopicName");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.eventTypes"), 7, 4, "stepEventType");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.batchJobs"), 7, 4, "stepBatchJob");
            addSignalMatches(score, signals, textList(step, "runtimeFingerprints.hostPatterns"), 7, 4, "stepHostPattern");
            addSignalMatches(score, signals, textList(step, "completionSignals"), 8, 5, "stepCompletionSignal");
        }

        if (anyOverlap(textList(process, "systems.internal"), matchedIds(systemMatches))
                || anyOverlap(textList(process, "systems.external"), matchedIds(systemMatches))) {
            score.add(8, "processSystemMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreBoundedContext(
            Map<String, Object> boundedContext,
            OperationalContextIncidentSignals signals,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> processMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> repositoryMatches
    ) {
        var score = new OperationalContextMatchScore();
        addIdentityMatches(score, signals, boundedContext, "boundedContext");
        addSignalMatches(score, signals, genericSignals(boundedContext), 9, 5, "signal");
        addSignalMatches(score, signals, textListAny(boundedContext, "systems", "scope.systemIds"), 6, 3, "system");
        addSignalMatches(score, signals, textListAny(boundedContext, "repos", "scope.repositoryIds"), 6, 3, "repo");
        addSignalMatches(score, signals, textListAny(boundedContext, "processes", "scope.processIds"), 5, 3, "process");
        addSignalMatches(score, signals, textList(boundedContext, "terms"), 6, 3, "term");
        addSignalMatches(score, signals, textList(boundedContext, "scope.businessDomains"), 4, 2, "businessDomain");
        addSignalMatches(score, signals, textList(boundedContext, "scope.capabilities"), 5, 3, "capability");
        addSignalMatches(score, signals, textList(boundedContext, "boundaries.incomingInputs"), 5, 3, "incomingInput");
        addSignalMatches(score, signals, textList(boundedContext, "boundaries.outgoingOutputs"), 5, 3, "outgoingOutput");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.serviceNames"), 9, 5, "serviceName");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.containerNames"), 9, 5, "containerName");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.endpointPrefixes"), 9, 5, "endpointPrefix");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.queueNames"), 8, 5, "queueName");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.topicNames"), 8, 5, "topicName");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.databaseSchemas"), 8, 5, "databaseSchema");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.packagePrefixes"), 9, 5, "packagePrefix");
        addSignalMatches(score, signals, textList(boundedContext, "runtimeFingerprints.logMarkers"), 6, 3, "logMarker");
        addSignalMatches(score, signals, textList(boundedContext, "incidentHints.likelySymptoms"), 6, 3, "symptom");
        addSignalMatches(score, signals, textList(boundedContext, "observability.expectedBusinessSignals"), 6, 3, "businessSignal");

        for (var term : mapList(boundedContext, "ubiquitousLanguage.keyTerms")) {
            addSignalMatch(score, signals, text(term, "term"), 8, 5, "ubiquitousTerm");
            addSignalMatches(score, signals, textList(term, "synonyms"), 6, 3, "ubiquitousSynonym");
        }

        for (var relation : mapList(boundedContext, "relationships.relationMap")) {
            addSignalMatch(score, signals, text(relation, "targetContextId"), 5, 3, "targetContext");
            addSignalMatches(score, signals, textList(relation, "viaSystemIds"), 6, 3, "relationSystem");
            addSignalMatches(score, signals, textList(relation, "viaIntegrationIds"), 6, 3, "relationIntegration");
            addSignalMatches(score, signals, textList(relation, "publishedLanguage"), 6, 3, "publishedLanguage");
        }
        for (var relation : mapList(boundedContext, "relations")) {
            addSignalMatch(score, signals, text(relation, "target"), 5, 3, "targetContext");
            addSignalMatches(score, signals, textList(relation, "via"), 6, 3, "relationVia");
        }

        if (anyOverlap(textList(boundedContext, "scope.systemIds"), matchedIds(systemMatches))) {
            score.add(8, "scopeSystemMatches");
        }
        if (anyOverlap(textList(boundedContext, "scope.processIds"), matchedIds(processMatches))) {
            score.add(8, "scopeProcessMatches");
        }
        if (anyOverlap(textList(boundedContext, "scope.repositoryIds"), matchedIds(repositoryMatches))) {
            score.add(8, "scopeRepositoryMatches");
        }

        return score;
    }

    private OperationalContextMatchScore scoreTeam(
            Map<String, Object> team,
            List<OperationalContextMatchedEntry<Map<String, Object>>> systemMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> processMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> repositoryMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> boundedContextMatches,
            List<OperationalContextMatchedEntry<Map<String, Object>>> integrationMatches
    ) {
        var score = new OperationalContextMatchScore();
        var teamId = text(team, "id");
        if (!StringUtils.hasText(teamId)) {
            return score;
        }

        addTeamOwnershipMatches(score, teamId, systemMatches, "system");
        addTeamOwnershipMatches(score, teamId, processMatches, "process");
        addTeamOwnershipMatches(score, teamId, repositoryMatches, "repository");
        addTeamOwnershipMatches(score, teamId, boundedContextMatches, "boundedContext");
        addTeamOwnershipMatches(score, teamId, integrationMatches, "integration");

        if (anyOverlap(textListAny(team, "owns.systems", "ownership.systems"), matchedIds(systemMatches))) {
            score.add(8, "ownsMatchedSystem");
        }
        if (anyOverlap(textListAny(team, "owns.processes", "scope.processes"), matchedIds(processMatches))) {
            score.add(8, "ownsMatchedProcess");
        }
        if (anyOverlap(textListAny(team, "owns.repos", "ownership.repositories"), matchedIds(repositoryMatches))) {
            score.add(8, "ownsMatchedRepository");
        }
        if (anyOverlap(textListAny(team, "owns.contexts", "scope.boundedContexts"), matchedIds(boundedContextMatches))) {
            score.add(8, "ownsMatchedBoundedContext");
        }
        if (anyOverlap(textListAny(team, "owns.integrations", "ownership.integrations"), matchedIds(integrationMatches))) {
            score.add(8, "ownsMatchedIntegration");
        }

        return score;
    }

    private OperationalContextMatchScore scoreGlossaryTerm(
            GlossaryTerm term,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        addSignalMatch(score, signals, term.id(), 8, 4, "termId");
        addSignalMatch(score, signals, term.term(), 9, 5, "term");
        addSignalMatch(score, signals, term.category(), 3, 1, "termCategory");
        addSignalMatch(score, signals, term.definition(), 3, 1, "definition");
        addSignalMatches(score, signals, term.useInContext(), 4, 2, "useContext");
        addSignalMatches(score, signals, term.doNotConfuseWith(), 4, 2, "doNotConfuse");
        addSignalMatches(score, signals, term.typicalEvidenceSignals(), 8, 5, "evidenceSignal");
        addSignalMatches(score, signals, term.canonicalReferences(), 6, 3, "canonicalReference");
        addSignalMatches(score, signals, term.synonyms(), 7, 4, "synonym");
        addSignalMatches(score, signals, term.notes(), 3, 1, "note");
        return score;
    }

    private OperationalContextMatchScore scoreHandoffRule(
            HandoffRule rule,
            OperationalContextIncidentSignals signals
    ) {
        var score = new OperationalContextMatchScore();
        score.add("retain-with-current-owner".equals(rule.id()) ? 4 : 8, "selectedByIncidentPattern");
        addSignalMatch(score, signals, rule.id(), 6, 3, "ruleId");
        addSignalMatch(score, signals, rule.title(), 5, 2, "ruleTitle");
        addSignalMatch(score, signals, rule.routeTo(), 5, 2, "routeTo");
        addSignalMatches(score, signals, rule.useWhen(), 4, 2, "useWhen");
        addSignalMatches(score, signals, rule.doNotUseWhen(), 3, 1, "doNotUseWhen");
        addSignalMatches(score, signals, rule.requiredEvidence(), 3, 1, "requiredEvidence");
        addSignalMatches(score, signals, rule.expectedFirstAction(), 3, 1, "firstAction");
        addSignalMatches(score, signals, rule.partnerTeams(), 4, 2, "partnerTeam");
        addSignalMatches(score, signals, rule.notes(), 2, 1, "ruleNote");
        return score;
    }

    private void addIdentityMatches(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            Map<String, Object> entry,
            String label
    ) {
        addSignalMatch(score, signals, text(entry, "id"), 10, 5, label + "Id");
        addSignalMatch(score, signals, text(entry, "name"), 7, 3, label + "Name");
        addSignalMatch(score, signals, text(entry, "shortName"), 6, 3, label + "ShortName");
    }

    private void addTeamOwnershipMatches(
            OperationalContextMatchScore score,
            String teamId,
            List<OperationalContextMatchedEntry<Map<String, Object>>> matches,
            String relationLabel
    ) {
        for (var match : matches) {
            var entry = match.entry();
            var entryId = text(entry, "id");
            if (normalize(teamId).equals(normalize(text(entry, "ownerTeamId")))
                    || containsId(entry, "ownership.owningTeamIds", teamId)
                    || containsId(entry, "scope.owningTeamIds", teamId)
                    || containsId(entry, "incidentHints.likelyOwningTeamIds", teamId)
                    || containsId(entry, "incidentRouting.likelyOwningTeamIds", teamId)) {
                score.add(12, relationLabel + "Owner=" + entryId);
            }
            if (containsId(entry, "partnerTeamIds", teamId)
                    || containsId(entry, "ownership.supportingTeamIds", teamId)
                    || containsId(entry, "scope.supportingTeamIds", teamId)
                    || containsId(entry, "incidentHints.likelyPartnerTeamIds", teamId)
                    || containsId(entry, "incidentRouting.likelyPartnerTeamIds", teamId)) {
                score.add(7, relationLabel + "Partner=" + entryId);
            }
        }
    }

    private void addSignalMatches(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            List<String> candidates,
            int exactPoints,
            int containsPoints,
            String reasonPrefix
    ) {
        for (var candidate : candidates) {
            addSignalMatch(score, signals, candidate, exactPoints, containsPoints, reasonPrefix);
        }
    }

    private void addSignalMatch(
            OperationalContextMatchScore score,
            OperationalContextIncidentSignals signals,
            String candidate,
            int exactPoints,
            int containsPoints,
            String reasonPrefix
    ) {
        var normalizedCandidate = normalize(candidate);
        if (!StringUtils.hasText(normalizedCandidate)
                || !OperationalContextMatchingSupport.isMeaningfulSignal(normalizedCandidate)) {
            return;
        }

        if (signals.containsExact(candidate)) {
            score.add(exactPoints, reasonPrefix + "=" + candidate);
        } else if (signals.contains(candidate)) {
            score.add(containsPoints, reasonPrefix + "=" + candidate);
        }
    }

}
