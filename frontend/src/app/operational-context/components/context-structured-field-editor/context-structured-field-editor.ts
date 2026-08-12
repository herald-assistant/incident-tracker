import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  OperationalContextReferenceOption,
  OperationalContextReferenceOptions,
  OperationalContextWritableType
} from '../../models/operational-context-maintenance.models';
import { OperationalContextFormField } from '../context-entity-editor-drawer/operational-context-form-adapter';

type JsonObject = Record<string, unknown>;
type ParticipantGroup = 'source' | 'targets' | 'intermediaries' | 'finalTargets';
type ProcessSystemRole = 'primarySystems' | 'supportingSystems' | 'externalSystems' | 'platformComponents';
type SignalStrength = 'exact' | 'strong' | 'medium' | 'weak';

interface MatchSignalRow {
  strength: SignalStrength;
  key: string;
  values: string[];
}

interface StructuredListGroup {
  key: string;
  label: string;
  tooltip: string;
}

interface ReferenceGroup {
  key: string;
  label: string;
  type: OperationalContextWritableType;
}

interface ParticipantRow {
  group: ParticipantGroup;
  index: number;
  label: string;
  removable: boolean;
}

const REFERENCE_GROUPS: Record<string, ReferenceGroup[]> = {
  system: [
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('teams', 'Teams', 'team'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  repository: [
    group('systems', 'Systems', 'system'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  process: [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  integration: [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  'bounded-context': [
    group('systems', 'Systems', 'system'),
    group('processes', 'Processes', 'process'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term'),
    group('teams', 'Teams', 'team'),
    group('handoffRules', 'Handoff rules', 'handoff-rule')
  ],
  'handoff-rule': [
    group('systems', 'Systems', 'system'),
    group('repositories', 'Repositories', 'repository'),
    group('processes', 'Processes', 'process'),
    group('boundedContexts', 'Bounded contexts', 'bounded-context'),
    group('integrations', 'Integrations', 'integration'),
    group('terms', 'Glossary terms', 'glossary-term')
  ]
};

const PROCESS_SYSTEM_ROLES: Array<{ key: ProcessSystemRole; label: string }> = [
  { key: 'primarySystems', label: 'Primary systems' },
  { key: 'supportingSystems', label: 'Supporting systems' },
  { key: 'externalSystems', label: 'External systems' },
  { key: 'platformComponents', label: 'Platform components' }
];

const PROCESS_STEP_REFERENCE_GROUPS: ReferenceGroup[] = [
  group('systems', 'Systems', 'system'),
  group('repositories', 'Repositories', 'repository'),
  group('boundedContexts', 'Bounded contexts', 'bounded-context'),
  group('integrations', 'Integrations', 'integration'),
  group('terms', 'Glossary terms', 'glossary-term'),
  group('handoffRules', 'Handoff rules', 'handoff-rule')
];

const SIGNAL_STRENGTHS: Array<{ key: SignalStrength; label: string }> = [
  { key: 'exact', label: 'Exact' },
  { key: 'strong', label: 'Strong' },
  { key: 'medium', label: 'Medium' },
  { key: 'weak', label: 'Weak' }
];

const SIGNAL_KEY_SUGGESTIONS: Partial<Record<OperationalContextWritableType, string[]>> = {
  system: ['serviceNames', 'deploymentNames', 'applicationNames', 'routes', 'endpoints', 'projectNames', 'projectPaths', 'businessTerms', 'aliases', 'configKeys'],
  repository: ['projectPaths', 'projectNames', 'buildCoordinates', 'groupIds', 'artifactIds', 'configKeys', 'aliases'],
  process: ['businessTerms', 'routes', 'operationNames', 'eventNames', 'exchanges', 'routingKeys', 'aliases'],
  integration: ['routes', 'endpoints', 'operationNames', 'bindings', 'exchanges', 'routingKeys', 'consumerGroups', 'hostPatterns', 'configKeys', 'artifactIds', 'businessTerms'],
  'bounded-context': ['businessTerms', 'routes', 'packagePrefixes', 'classNames', 'dbTables', 'schedulerNames', 'exchanges', 'routingKeys', 'consumerGroups', 'configKeys', 'artifactIds'],
  team: ['teamNames', 'aliases', 'emailAliases', 'collaborationIds'],
  'glossary-term': ['phrases', 'aliases', 'fieldNames', 'businessTerms', 'word', 'phrase', 'field', 'class', 'const', 'enum', 'variable', 'value', 'endpoint', 'table', 'system', 'integration', 'bounded-context']
};

const RELATION_TARGET_TYPES: ReferenceGroup[] = [
  group('system', 'System', 'system'),
  group('repository', 'Repository', 'repository'),
  group('code-search-scope', 'Code-search scope', 'code-search-scope'),
  group('process', 'Process', 'process'),
  group('integration', 'Integration', 'integration'),
  group('bounded-context', 'Bounded context', 'bounded-context'),
  group('team', 'Team', 'team'),
  group('glossary-term', 'Glossary term', 'glossary-term'),
  group('handoff-rule', 'Handoff rule', 'handoff-rule')
];

const DATA_ARTIFACT_GROUPS: StructuredListGroup[] = [
  { key: 'primaryObjects', label: 'Primary business objects', tooltip: 'dataPrimaryObjects' },
  { key: 'inputArtifacts', label: 'Input artifacts', tooltip: 'dataInputArtifacts' },
  { key: 'outputArtifacts', label: 'Output artifacts', tooltip: 'dataOutputArtifacts' },
  { key: 'persistedEntities', label: 'Persisted entities', tooltip: 'dataPersistedEntities' },
  { key: 'readModels', label: 'Read models', tooltip: 'dataReadModels' },
  { key: 'auditArtifacts', label: 'Audit artifacts', tooltip: 'dataAuditArtifacts' },
  { key: 'notes', label: 'Notes', tooltip: 'dataArtifactNotes' }
];

const PROCESS_BOUNDARY_LIST_GROUPS: StructuredListGroup[] = [
  { key: 'startsWhen', label: 'Starts when', tooltip: 'boundaryStartsWhen' },
  { key: 'endsWhen', label: 'Ends when', tooltip: 'boundaryEndsWhen' },
  { key: 'includes', label: 'Includes', tooltip: 'boundaryIncludes' },
  { key: 'excludes', label: 'Excludes', tooltip: 'boundaryExcludes' },
  { key: 'assumptions', label: 'Assumptions', tooltip: 'boundaryAssumptions' }
];

const PROCESS_LIFECYCLE_LIST_GROUPS: StructuredListGroup[] = [
  { key: 'entryCriteria', label: 'Entry criteria', tooltip: 'lifecycleEntryCriteria' },
  { key: 'statuses', label: 'Statuses', tooltip: 'lifecycleStatuses' },
  { key: 'terminalStates', label: 'Terminal states', tooltip: 'lifecycleTerminalStates' }
];

const PROCESS_LIFECYCLE_OUTCOME_GROUPS: StructuredListGroup[] = [
  { key: 'successOutcomes', label: 'Success outcomes', tooltip: 'lifecycleSuccessOutcomes' },
  { key: 'partialOutcomes', label: 'Partial outcomes', tooltip: 'lifecyclePartialOutcomes' },
  { key: 'failedOutcomes', label: 'Failed outcomes', tooltip: 'lifecycleFailedOutcomes' },
  { key: 'cancellationOutcomes', label: 'Cancellation outcomes', tooltip: 'lifecycleCancellationOutcomes' }
];

const COMPLETION_SIGNAL_GROUPS: StructuredListGroup[] = [
  { key: 'successful', label: 'Successful', tooltip: 'completionSuccessful' },
  { key: 'partial', label: 'Partial', tooltip: 'completionPartial' },
  { key: 'failed', label: 'Failed', tooltip: 'completionFailed' },
  { key: 'cancelled', label: 'Cancelled', tooltip: 'completionCancelled' }
];

const BOUNDED_SCOPE_GROUPS: StructuredListGroup[] = [
  { key: 'includes', label: 'Includes', tooltip: 'boundedScopeIncludes' },
  { key: 'excludes', label: 'Excludes', tooltip: 'boundedScopeExcludes' },
  { key: 'businessCapabilities', label: 'Business capabilities', tooltip: 'boundedScopeCapabilities' },
  { key: 'coreEntities', label: 'Core entities', tooltip: 'boundedScopeEntities' },
  { key: 'keyDecisions', label: 'Key decisions', tooltip: 'boundedScopeDecisions' }
];

const BOUNDED_SEMANTIC_GROUPS: StructuredListGroup[] = [
  { key: 'coreConcepts', label: 'Core concepts', tooltip: 'boundedCoreConcepts' },
  { key: 'localConcepts', label: 'Local concepts', tooltip: 'boundedLocalConcepts' },
  { key: 'canonicalEntities', label: 'Canonical entities', tooltip: 'boundedCanonicalEntities' },
  { key: 'commands', label: 'Commands', tooltip: 'boundedCommands' },
  { key: 'events', label: 'Events', tooltip: 'boundedEvents' },
  { key: 'invariants', label: 'Invariants', tooltip: 'boundedInvariants' },
  { key: 'ownsLanguage', label: 'Owns language', tooltip: 'boundedOwnsLanguage' },
  { key: 'doesNotOwn', label: 'Does not own', tooltip: 'boundedDoesNotOwn' }
];

const PROCESS_TRIGGER_TYPES = ['api', 'event', 'command'];

const SOURCE_COVERAGE_STATUSES = ['complete', 'partial', 'unknown', 'full', 'scanned', 'fully-scanned'];
const GAP_SEVERITIES = ['error', 'warning', 'info'];
const GAP_STATUSES = ['open', 'resolved'];

const FIELD_TOOLTIPS: Record<string, string> = {
  ownershipStatus: 'Choose explicit when the catalogue deliberately assigns an owner. Choose unknown when ownership is not confirmed. Runtime gives explicit bounded-context ownership priority over system ownership.',
  ownerTeamIds: 'Select existing team IDs. They are validated and used by the ownership resolver and AI handoff. Selecting a team does not copy or rename it.',
  ownerLabel: 'Use only when the responsible party has no canonical team entry. Runtime exposes this label as the resolved fallback owner.',
  confidence: 'Choose high, medium or low to state how strongly the maintained source supports this ownership or handoff fact.',
  source: 'Describe the durable source that confirms ownership, for example an anonymized CRM domain catalogue. It is shown as provenance.',
  notes: 'Enter one durable clarification per line. Notes are preserved for detailed AI context but do not create ownership or graph edges.',
  systemExternalOwner: 'Enter a durable organization or provider label only when this entire system boundary is operated outside the local team catalogue. opctx_get_entity exposes the label as external responsibility; it never creates local team ownership.',
  runtimeConfigurationDirectory: 'Enter the repository-relative directory that contains this internal service runtime configuration. Config Drift Viewer uses it to select the configuration scope. Use Recognition signals for service or deployment names instead of duplicating them here.',
  repositoryEvidenceSourceRef: 'Enter a stable relative path or durable document label supporting this repository mapping. The value is exposed as provenance to operators and AI; the application does not read the referenced content automatically.',
  repositoryEvidenceType: 'Classify what the source proves, for example build-definition, module-cluster, repository-documentation or architecture-decision. AI uses the type to interpret provenance; it does not change confidence automatically.',
  repositoryEvidenceNote: 'Optionally summarize the anonymized fact supported by this source. The note is context for operators and AI, not copied source content or proof of a current incident.',
  repositoryAnswerWhenMentioned: 'Enter one durable phrase per line that should make this repository relevant during catalogue search and AI exploration, for example CRM contact validation. These phrases guide discovery but do not define code-search access.',
  repositoryDisambiguateFrom: 'Enter one nearby repository, concept or responsibility per line that must not be confused with this repository. opctx_get_entity exposes these contrasts so AI can avoid selecting the wrong code source.',
  boundedLocalLanguage: 'Enter one complete statement per line explaining how a term is understood inside this CRM context. Runtime indexes these statements and AI uses them to translate evidence without borrowing meaning from another context.',
  boundedScopeIncludes: 'Enter one durable responsibility that belongs inside this bounded context per line. AI treats these items as in-scope when explaining functional impact; they do not create executable behavior.',
  boundedScopeExcludes: 'Enter one adjacent responsibility deliberately outside this bounded context per line. AI should stop or hand off at these boundaries instead of attributing the behavior locally.',
  boundedScopeCapabilities: 'Enter one stable business capability served by this context per line, for example CRM Contact Preference Management. Capabilities help search and AI explain why the boundary exists; they are not separate catalogue entities.',
  boundedScopeEntities: 'Enter one canonical domain entity or aggregate kind owned or centered here per line. AI uses the names to map evidence to domain responsibility; never paste real CRM records or identifiers.',
  boundedScopeDecisions: 'Enter one durable business decision owned by this context per line. AI uses these statements to explain decision responsibility; they do not configure rules or workflows.',
  boundedCoreConcepts: 'Enter the concepts essential to this context, one per line. They are indexed and exposed to AI as the semantic core of the boundary.',
  boundedLocalConcepts: 'Enter terms whose meaning is specific to this context, one per line. AI uses them with the local-language summary to avoid applying a generic or neighboring meaning.',
  boundedCanonicalEntities: 'Enter canonical domain entity names used by this context, one per line. These names guide semantic interpretation but do not create database or code references.',
  boundedCommands: 'Enter durable business command names accepted by this context, one per line. AI uses them to understand intent and direction; the catalogue never executes a command.',
  boundedEvents: 'Enter durable domain event names emitted or interpreted by this context, one per line. AI may match them with evidence, but their presence here does not prove an event occurred.',
  boundedInvariants: 'Enter one rule that must remain true inside the context per line. AI treats invariants as semantic constraints, not as executable validation or proof of current runtime state.',
  boundedOwnsLanguage: 'Enter one phrase whose authoritative meaning belongs to this context per line. AI uses ownership of language to resolve ambiguity and handoffs.',
  boundedDoesNotOwn: 'Enter one nearby phrase or responsibility explicitly owned elsewhere per line. AI uses the contrast to prevent semantic conflation and unsupported ownership attribution.',
  boundedEvidenceSourceRef: 'Enter a stable safe relative path or durable source label supporting this bounded-context model. Operators and AI see it as provenance; runtime does not fetch the source automatically.',
  boundedEvidenceType: 'Classify the supporting source, for example domain-documentation, glossary-review, workshop-notes, contract-spec or source-code. The type explains provenance but does not automatically increase confidence.',
  boundedEvidenceNote: 'Optionally summarize the anonymized semantic fact supported by this source. Never paste CRM customer data, credentials or copied source content.',
  boundedAnswerWhenMentioned: 'Enter one durable user or evidence phrase per line that should make this bounded context relevant. The phrases improve discovery and AI selection but do not create graph edges.',
  boundedDisambiguateFrom: 'Enter one nearby context, concept or responsibility per line that must not be confused with this context. AI uses these explicit contrasts during interpretation.',
  boundedUsefulSearchKeywords: 'Enter one stable, non-sensitive domain or technical keyword per line that can help identify this context. Keywords guide discovery only; code-search access still comes from canonical scopes.',
  boundedExplanationStyle: 'Describe briefly how AI should frame this context in explanations, for example as the CRM contact-preference boundary. This affects presentation guidance, not facts, ownership or runtime behavior.',
  reference: 'Select an existing canonical entity. The selection creates a graph edge, appears in related-entity reads and can block deletion of the target.',
  targetType: 'Strictly system or bounded-context. This decides which catalogue list supplies the target and which semantic-to-code bridge runtime builds.',
  targetId: 'Select the existing system or bounded context whose code this scope locates. Flow Explorer, code search and ownership navigation consume this target.',
  repoId: 'Select an existing repository. Runtime resolves its Git identity and uses it as a code-search source.',
  role: 'Describe why the repository belongs in the scope. primary marks the main source; common roles are library, supporting, shared, reference, legacy and migration-peer.',
  priority: 'Enter a positive integer. 1 means read this repository first; runtime and AI use ascending priority as exploration order.',
  searchMode: 'Choose whole-repository or path-prefixes. Runtime applies this as the GitLab search boundary.',
  pathPrefixes: 'For path-prefixes, enter safe relative paths without a leading slash, backslash or .., one per line. Leave empty for whole-repository.',
  reason: 'Explain in business/system language why this repository is part of the scope. AI uses the reason to justify source selection.',
  readFor: 'Enter one question per line that code reading should answer. AI uses these as exploration goals, not as known answers.',
  participantSystem: 'Select an existing system taking this role in the integration. Runtime creates directed participant edges and AI uses them to explain the boundary.',
  participantContext: 'Optionally select the bounded context represented by this participant. It refines semantic ownership and impact.',
  participantRole: 'Describe the participant role, for example client, server, producer, consumer or mediator. It explains direction but does not define ownership.',
  participantExternalOwner: 'Use a durable external owner label only when the participant is outside the local catalogue. It is descriptive, not a local team assignment.',
  participantNotes: 'Enter one participant-specific clarification per line. Notes do not replace the system/context selection.',
  gitProvider: 'Enter the Git provider name. Runtime accepts an empty provider as GitLab-compatible, while gitlab is the explicit current convention used by code tools.',
  gitGroup: 'Enter the provider-relative group or namespace. GitLab repository discovery uses it to check whether the project belongs to the configured session group.',
  gitProject: 'Enter the project slug or name. It is a fallback repository candidate for GitLab tools and satisfies maintenance validation when projectPath is unavailable.',
  gitProjectPath: 'Enter the full provider-relative group/project path without a host URL. Repository discovery, ownership resolution and deterministic GitLab lookup use this value directly.',
  gitDefaultBranch: 'Enter the durable default branch advertised in code-search read models. It provides context to AI and operators; runtime branch evidence can still select another branch.',
  gitUrl: 'Optionally enter a navigable repository URL. It is exposed in read models for operators but projectPath remains the canonical lookup identity.',
  gitAliases: 'Enter one stable alternate repository or project name per line. Runtime adds aliases to repository matching and GitLab candidate resolution.',
  processActors: 'Enter durable business or operator roles, one per line. AI uses actors to explain the user journey; they do not create ownership or system graph edges.',
  processSystemRole: 'Select an existing canonical system. The selected role becomes a typed process graph edge used by related-entity views, Flow Explorer and AI context.',
  stepId: 'Enter a stable lowercase kebab-case ID unique inside this process. Runtime uses process-id/step-id as the graph identity and indexes it as a search signal.',
  stepName: 'Enter a short milestone name. It is indexed as a process signal and shown to AI when reconstructing the ordered flow.',
  stepType: 'Classify the milestone, for example user-action, business-step, system-step or system-handoff. Runtime exposes the value as relation evidence and AI flow context.',
  stepSummary: 'Describe the observable milestone in business/system language. Runtime indexes the summary and AI uses it to explain what happens at this point.',
  stepReference: 'Select existing catalogue entities directly involved in this milestone. Runtime creates graph edges from the process step and delete impact can use those edges.',
  stepStrongTerms: 'Enter one durable business phrase per line. The UI stores them as matchSignals.strong.terms; runtime indexes them as strong recognition signals for AI and resolvers.',
  stepOrder: 'The array order is the process order used by UI and AI. Move the card up or down to change the sequence; IDs remain stable.',
  boundaryBusinessCapability: 'Enter the durable CRM business capability enclosed by this process. Runtime exposes it to AI as the functional scope label; it does not create a separate catalogue entity or ownership assignment.',
  boundaryStartsWhen: 'Enter one observable condition that starts the process per line. AI uses these conditions to recognize the beginning of the functional flow; do not enter low-level method calls unless they are the only durable boundary evidence.',
  boundaryEndsWhen: 'Enter one observable condition that means the process has left its intended scope per line. Runtime indexes and exposes these conditions so AI stops following unrelated downstream activity.',
  boundaryIncludes: 'Enter one responsibility or activity that belongs inside this process per line. AI uses the list to explain in-scope behavior and impact; it does not create executable workflow steps.',
  boundaryExcludes: 'Enter one adjacent responsibility deliberately outside this process per line. AI treats these as hard analysis boundaries and should hand off instead of attributing the excluded behavior to this process.',
  boundaryAssumptions: 'Enter one durable assumption required to interpret the boundary per line. AI must present assumptions as limitations, never as confirmed runtime evidence.',
  lifecycleTriggerType: 'Choose or enter the stable trigger category. Current CRM catalogue values are api, event and command. The value describes how the lifecycle starts; it does not register a listener or endpoint.',
  lifecycleTriggerName: 'Enter a concise durable name for the CRM trigger. Runtime indexes and exposes it so AI can connect an observed request, event or command to the process lifecycle.',
  lifecycleTriggerExchange: 'For an event or message trigger, optionally enter the stable exchange/topic/channel name. AI may match it with evidence; never enter credentials, tenant IDs or transient message identifiers.',
  lifecycleEntryCriteria: 'Enter one prerequisite that must already hold before the process can enter its lifecycle per line. AI uses these criteria to distinguish a process that never started from one that failed after entry.',
  lifecycleStatuses: 'Enter one durable business or system lifecycle status per line. These values ground AI state interpretation and transition validation; they do not configure an executable state machine.',
  lifecycleTransitionFrom: 'Enter the source status. Leave empty only for an initial transition; a documented wildcard such as any-non-terminal may be used when it is part of the CRM lifecycle vocabulary.',
  lifecycleTransitionTo: 'Enter the required target status reached by this transition. AI uses from/to to reconstruct state progress; the value does not cause a runtime state change.',
  lifecycleTransitionTrigger: 'Describe the observable action or event that moves the CRM process between the two states. AI uses it as transition context, not as proof that the transition occurred.',
  lifecycleTerminalStates: 'Enter one status after which the process lifecycle no longer advances per line. AI uses terminal states to distinguish completion or cancellation from an in-progress delay.',
  lifecycleSuccessOutcomes: 'Enter one durable business outcome produced by a successful terminal path per line. Outcomes explain meaning; completion signals separately describe evidence that the outcome occurred.',
  lifecyclePartialOutcomes: 'Enter one meaningful non-terminal or partially completed outcome per line. AI uses this to explain incomplete progress without incorrectly declaring full success.',
  lifecycleFailedOutcomes: 'Enter one durable failed lifecycle outcome per line. This is a known outcome category, not evidence that a current CRM incident has that cause.',
  lifecycleCancellationOutcomes: 'Enter one durable cancellation outcome per line. AI uses it to separate deliberate cancellation from technical failure.',
  completionSuccessful: 'Enter one observable fact per line proving the CRM process reached an expected successful result. AI compares incident evidence with these signals; the catalogue text alone never proves completion.',
  completionPartial: 'Enter one observable fact per line showing progress without full completion. AI uses it to distinguish an in-flight or incomplete process from a failed process.',
  completionFailed: 'Enter one observable fact per line showing the process did not complete successfully. Describe evidence, not an assumed root cause.',
  completionCancelled: 'Enter one observable fact per line showing deliberate or business-driven cancellation. AI uses it to avoid classifying cancellation as an infrastructure failure.',
  signalStrength: 'Exact means a value uniquely identifies this entity; strong is highly characteristic; medium needs supporting context; weak is only a discovery hint. Runtime and AI retain this confidence distinction when explaining a match.',
  signalKey: 'Choose the evidence field whose values are being matched. Suggestions reflect this entity type and the current catalogue; the backend also accepts a durable custom key when a real runtime source uses it.',
  signalValues: 'Enter one durable value per line. Resolver search compares these strings with evidence, while AI uses them to explain why this entity is relevant. Do not enter secrets, transient IDs or example-only placeholders.',
  relationType: 'Enter the semantic edge label, for example depends-on, uses, supports, implements, followed-by or hands-off-to. Runtime exposes this label in the relation graph; it does not execute orchestration.',
  relationTargetType: 'Choose the canonical catalogue type at the other end of the edge. This controls the target picker and the graph entity type consumed by runtime and AI.',
  relationTarget: 'Select an existing canonical entity. Runtime creates a navigable graph edge and delete impact may block removal of the target. Self-reference is not allowed.',
  relationExternalTarget: 'Use only when the other side genuinely has no canonical catalogue entry. The label is preserved for AI context but does not create a navigable internal graph edge.',
  relationVia: 'Optionally select integrations that explain how this relation is realized. They are preserved as relation context; the canonical target remains the graph destination.',
  relationEvidence: 'Explain the durable evidence or business reason for this edge. AI and operators use it to understand why the relation exists; it does not increase match confidence automatically.',
  processFailureId: 'Enter a stable lowercase kebab-case ID unique inside this process. It keeps the failure mode identifiable across catalogue updates; it is not an incident or error ID.',
  processFailureName: 'Enter a short recognizable failure name. Catalogue detail and AI tools expose it as the human-readable hypothesis label.',
  processFailureSummary: 'Describe what fails or stops in observable terms. AI uses this as grounded hypothesis context, but it never proves root cause without incident evidence.',
  processFailureStep: 'Optionally enter the exact ID of an existing process step affected by this failure. The backend validates the reference and AI can place the failure in the ordered flow.',
  processFailureSignals: 'Enter one observable symptom per line, such as a missing CRM confirmation or a stable error category. These values are indexed for search and supplied to AI; do not put unsupported causes here.',
  integrationFailureName: 'Enter a concise boundary failure name. It is exposed in catalogue detail and AI tool signals as the hypothesis label.',
  integrationFailureType: 'Choose or enter a stable category such as timeout, upstream-error, rejected-message or unavailable. AI uses the category to explain propagation style; it does not trigger runtime handling.',
  integrationFailureSymptom: 'Describe what an operator or caller can observe at the integration boundary. AI compares evidence with this symptom before using the failure mode as a hypothesis.',
  integrationFailureImpact: 'Describe the durable process or system consequence if this boundary failure occurs. AI uses it for impact and handoff context, not as evidence that the failure happened.',
  dataPrimaryObjects: 'Enter canonical business object or aggregate kinds central to the process, one per line. opctx_get_entity exposes and indexes them so AI can connect technical evidence to business concepts.',
  dataInputArtifacts: 'Enter message, document, command or request kinds consumed by the process, one per line. Describe types only; never paste real CRM records or payloads.',
  dataOutputArtifacts: 'Enter message, document, event or confirmation kinds produced by the process, one per line. AI uses them to reason about expected outcomes and downstream handoffs.',
  dataPersistedEntities: 'Enter durable entity or table-model kinds written by the process, one per line. These are semantic hints for AI and data diagnostics, not permission to query data.',
  dataReadModels: 'Enter durable projections or views read or produced by the process, one per line. AI uses them to distinguish read-side evidence from source-of-truth entities.',
  dataAuditArtifacts: 'Enter audit record or metadata kinds created by the process, one per line. Do not include real usernames, customer IDs or timestamps.',
  dataArtifactNotes: 'Enter one durable clarification about artifact semantics per line. Notes are exposed to AI but are not matching rules or executable workflow instructions.',
  coverageStatus: 'Choose how completely the maintained entry is grounded. Prefer complete, partial or unknown for new data. Legacy full, scanned and fully-scanned remain selectable without changing their meaning.',
  coverageScanned: 'Enter one durable source or repository area actually reviewed per line. opctx_get_entity exposes these as provenance; a name here does not create a catalogue reference.',
  coverageExpected: 'Enter one expected but not yet reviewed source per line. AI treats these as places where additional evidence may exist, not as confirmed facts.',
  coverageLimitations: 'Enter one concrete visibility limit per line. opctx_get_entity promotes these values to tool limitations, so AI must expose incomplete knowledge instead of assuming full coverage.',
  gapId: 'Optionally enter a stable lowercase kebab-case maintenance ID. It helps humans track the question but does not become a catalogue entity or graph target.',
  gapType: 'Classify why knowledge is missing, for example missing-evidence, unresolved-boundary, unconfirmed-ownership, planned-capability or planned-change. The type is descriptive and does not change runtime behavior.',
  gapSummary: 'State one concrete, actionable unresolved fact. This text becomes an Open Questions inbox item and is returned to AI as a knowledge limit.',
  gapSeverity: 'Choose error, warning or info. The maintenance inbox uses this value for prioritization; severity does not assert that a production incident exists.',
  gapStatus: 'Choose open while the fact is unresolved or resolved after confirmation. The status is exposed in the maintenance inbox and AI context; deleting the gap removes the question entirely.',
  gapNextSources: 'Enter one useful next evidence source per line. This guides maintainers and AI follow-up but does not automatically fetch or trust the source.'
};

function group(key: string, label: string, type: OperationalContextWritableType): ReferenceGroup {
  return { key, label, type };
}

@Component({
  selector: 'app-context-structured-field-editor',
  imports: [MatIconModule, MatTooltipModule],
  templateUrl: './context-structured-field-editor.html',
  styleUrl: './context-structured-field-editor.scss'
})
export class ContextStructuredFieldEditorComponent {
  readonly field = input.required<OperationalContextFormField>();
  readonly entityType = input.required<OperationalContextWritableType>();
  readonly entityId = input('');
  readonly value = input<unknown>(null);
  readonly referenceOptions = input<OperationalContextReferenceOptions>({});
  readonly readonly = input(false);
  readonly valueChange = output<unknown>();

  tooltip(key: string): string {
    return FIELD_TOOLTIPS[key] || 'This value is stored in the canonical operational context entry and exposed to runtime read models and AI tools.';
  }

  object(): JsonObject {
    return asObject(this.value());
  }

  objectText(key: string): string {
    return text(this.object()[key]);
  }

  objectList(key: string): string[] {
    return stringList(this.object()[key]);
  }

  updateObject(key: string, value: unknown): void {
    const next = { ...this.object() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateObjectList(key: string, raw: string): void {
    this.updateObject(key, lines(raw));
  }

  repositoryEvidence(): JsonObject[] {
    return objectList(this.value());
  }

  addRepositoryEvidence(): void {
    this.valueChange.emit([...this.repositoryEvidence(), { sourceRef: '', evidenceType: '' }]);
  }

  updateRepositoryEvidence(index: number, key: string, value: unknown): void {
    const evidence = this.repositoryEvidence().map((item) => ({ ...item }));
    assignOrDelete(evidence[index], key, value);
    this.valueChange.emit(evidence);
  }

  removeRepositoryEvidence(index: number): void {
    this.valueChange.emit(this.repositoryEvidence().filter((_, itemIndex) => itemIndex !== index));
  }

  boundedLocalLanguage(): string[] {
    return legacyTextList(this.value());
  }

  updateBoundedLocalLanguage(raw: string): void {
    this.valueChange.emit(lines(raw));
  }

  boundedScopeGroups(): StructuredListGroup[] {
    return BOUNDED_SCOPE_GROUPS;
  }

  boundedSemanticGroups(): StructuredListGroup[] {
    return BOUNDED_SEMANTIC_GROUPS;
  }

  boundedEvidence(): JsonObject[] {
    return objectList(this.value());
  }

  addBoundedEvidence(): void {
    this.valueChange.emit([...this.boundedEvidence(), { sourceRef: '', evidenceType: '' }]);
  }

  updateBoundedEvidence(index: number, key: string, value: unknown): void {
    const evidence = this.boundedEvidence().map((item) => ({ ...item }));
    assignOrDelete(evidence[index], key, value);
    this.valueChange.emit(evidence);
  }

  removeBoundedEvidence(index: number): void {
    this.valueChange.emit(this.boundedEvidence().filter((_, itemIndex) => itemIndex !== index));
  }

  processSystemRoles(): Array<{ key: ProcessSystemRole; label: string }> {
    return PROCESS_SYSTEM_ROLES;
  }

  selectedProcessSystems(role: ProcessSystemRole): string[] {
    return stringList(this.object()[role]);
  }

  availableProcessSystems(role: ProcessSystemRole): OperationalContextReferenceOption[] {
    const selected = new Set(PROCESS_SYSTEM_ROLES.flatMap((candidate) => this.selectedProcessSystems(candidate.key)));
    return this.optionsFor('system').filter((option) => !selected.has(option.id) || this.selectedProcessSystems(role).includes(option.id));
  }

  addProcessSystem(role: ProcessSystemRole, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject(role, unique([...this.selectedProcessSystems(role), id]));
    resetSelect(event);
  }

  removeProcessSystem(role: ProcessSystemRole, id: string): void {
    this.updateObject(role, this.selectedProcessSystems(role).filter((value) => value !== id));
  }

  ownershipTeamOptions(): OperationalContextReferenceOption[] {
    return this.optionsFor('team').filter((option) => !this.objectList('ownerTeamIds').includes(option.id));
  }

  addOwnerTeam(event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject('ownerTeamIds', unique([...this.objectList('ownerTeamIds'), id]));
    resetSelect(event);
  }

  removeOwnerTeam(id: string): void {
    this.updateObject('ownerTeamIds', this.objectList('ownerTeamIds').filter((value) => value !== id));
  }

  referenceGroups(): ReferenceGroup[] {
    return REFERENCE_GROUPS[this.entityType()] || [];
  }

  selectedReferences(key: string): string[] {
    return stringList(this.object()[key]);
  }

  availableReferences(referenceGroup: ReferenceGroup): OperationalContextReferenceOption[] {
    const selected = this.selectedReferences(referenceGroup.key);
    return this.optionsFor(referenceGroup.type).filter((option) =>
      !selected.includes(option.id)
      && !(referenceGroup.type === this.entityType() && option.id === this.entityId())
    );
  }

  addReference(referenceGroup: ReferenceGroup, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateObject(referenceGroup.key, unique([...this.selectedReferences(referenceGroup.key), id]));
    resetSelect(event);
  }

  removeReference(key: string, id: string): void {
    this.updateObject(key, this.selectedReferences(key).filter((value) => value !== id));
  }

  signalStrengths(): Array<{ key: SignalStrength; label: string }> {
    return SIGNAL_STRENGTHS;
  }

  signalRows(): MatchSignalRow[] {
    const source = this.object();
    const tiered = SIGNAL_STRENGTHS.some((strength) => Object.hasOwn(source, strength.key));
    if (!tiered) {
      return Object.entries(source)
        .filter(([, value]) => value === null || typeof value !== 'object' || Array.isArray(value))
        .map(([key, value]) => ({ strength: 'strong', key, values: stringList(value) }));
    }
    return SIGNAL_STRENGTHS.flatMap((strength) =>
      Object.entries(asObject(source[strength.key])).map(([key, value]) => ({
        strength: strength.key,
        key,
        values: stringList(value)
      }))
    );
  }

  signalKeySuggestions(): string[] {
    return unique([
      ...(SIGNAL_KEY_SUGGESTIONS[this.entityType()] || []),
      ...this.signalRows().map((row) => row.key)
    ]).sort((left, right) => left.localeCompare(right));
  }

  addSignal(): void {
    const rows = this.signalRows();
    const usedStrongKeys = new Set(rows.filter((row) => row.strength === 'strong').map((row) => row.key));
    const key = this.signalKeySuggestions().find((candidate) => !usedStrongKeys.has(candidate)) || 'customSignal';
    this.emitSignals([...rows, { strength: 'strong', key, values: [] }]);
  }

  updateSignal(index: number, field: 'strength' | 'key', value: string): void {
    const rows = this.signalRows().map((row) => ({ ...row, values: [...row.values] }));
    if (!rows[index]) return;
    if (field === 'strength' && isSignalStrength(value)) rows[index].strength = value;
    if (field === 'key' && value.trim()) rows[index].key = value.trim();
    this.emitSignals(rows);
  }

  updateSignalValues(index: number, raw: string): void {
    const rows = this.signalRows().map((row) => ({ ...row, values: [...row.values] }));
    if (!rows[index]) return;
    rows[index].values = unique(lines(raw));
    this.emitSignals(rows);
  }

  removeSignal(index: number): void {
    this.emitSignals(this.signalRows().filter((_, rowIndex) => rowIndex !== index));
  }

  failureModes(): JsonObject[] {
    const value = this.value();
    if (!Array.isArray(value)) return [];
    return value.map((item) => {
      if (item !== null && typeof item === 'object' && !Array.isArray(item)) return item as JsonObject;
      const summary = text(item);
      return this.entityType() === 'process' ? { name: summary, summary } : { name: summary, symptom: summary };
    });
  }

  addFailureMode(): void {
    const item = this.entityType() === 'process'
      ? { id: '', name: '', summary: '', signals: [] }
      : { name: '', type: '', symptom: '', impact: '' };
    this.valueChange.emit([...this.failureModes(), item]);
  }

  updateFailureMode(index: number, key: string, value: unknown): void {
    const modes = this.failureModes().map((item) => ({ ...item }));
    assignOrDelete(modes[index], key, value);
    this.valueChange.emit(modes);
  }

  updateFailureModeList(index: number, key: string, raw: string): void {
    this.updateFailureMode(index, key, lines(raw));
  }

  removeFailureMode(index: number): void {
    this.valueChange.emit(this.failureModes().filter((_, rowIndex) => rowIndex !== index));
  }

  processBoundary(): JsonObject {
    const value = this.value();
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) return value as JsonObject;
    const legacy = legacyTextList(value);
    return legacy.length ? { endsWhen: legacy } : {};
  }

  processBoundaryGroups(): StructuredListGroup[] {
    return PROCESS_BOUNDARY_LIST_GROUPS;
  }

  processBoundaryText(key: string): string {
    return text(this.processBoundary()[key]);
  }

  processBoundaryList(key: string): string[] {
    return stringList(this.processBoundary()[key]);
  }

  updateProcessBoundary(key: string, value: unknown): void {
    const next = { ...this.processBoundary() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateProcessBoundaryList(key: string, raw: string): void {
    this.updateProcessBoundary(key, lines(raw));
  }

  processLifecycle(): JsonObject {
    const value = this.value();
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) return value as JsonObject;
    const legacy = legacyTextList(value);
    return legacy.length ? { statuses: legacy } : {};
  }

  lifecycleListGroups(): StructuredListGroup[] {
    return PROCESS_LIFECYCLE_LIST_GROUPS;
  }

  lifecycleOutcomeGroups(): StructuredListGroup[] {
    return PROCESS_LIFECYCLE_OUTCOME_GROUPS;
  }

  lifecycleList(key: string): string[] {
    return stringList(this.processLifecycle()[key]);
  }

  lifecycleTriggers(): JsonObject[] {
    return objectList(this.processLifecycle()['triggers']);
  }

  triggerTypes(): string[] {
    return PROCESS_TRIGGER_TYPES;
  }

  addLifecycleTrigger(): void {
    this.updateProcessLifecycle('triggers', [...this.lifecycleTriggers(), { type: 'api', name: '' }]);
  }

  updateLifecycleTrigger(index: number, key: string, value: unknown): void {
    const triggers = this.lifecycleTriggers().map((item) => ({ ...item }));
    assignOrDelete(triggers[index], key, value);
    this.updateProcessLifecycle('triggers', triggers);
  }

  removeLifecycleTrigger(index: number): void {
    this.updateProcessLifecycle('triggers', this.lifecycleTriggers().filter((_, rowIndex) => rowIndex !== index));
  }

  lifecycleTransitions(): JsonObject[] {
    return objectList(this.processLifecycle()['transitions']);
  }

  addLifecycleTransition(): void {
    this.updateProcessLifecycle('transitions', [...this.lifecycleTransitions(), { from: '', to: '', trigger: '' }]);
  }

  updateLifecycleTransition(index: number, key: string, value: unknown): void {
    const transitions = this.lifecycleTransitions().map((item) => ({ ...item }));
    assignOrDelete(transitions[index], key, value);
    this.updateProcessLifecycle('transitions', transitions);
  }

  removeLifecycleTransition(index: number): void {
    this.updateProcessLifecycle('transitions', this.lifecycleTransitions().filter((_, rowIndex) => rowIndex !== index));
  }

  updateLifecycleList(key: string, raw: string): void {
    this.updateProcessLifecycle(key, lines(raw));
  }

  completionSignalGroups(): StructuredListGroup[] {
    return COMPLETION_SIGNAL_GROUPS;
  }

  completionSignals(): JsonObject {
    const value = this.value();
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) return value as JsonObject;
    const legacy = legacyTextList(value);
    return legacy.length ? { successful: legacy } : {};
  }

  completionSignalList(key: string): string[] {
    return stringList(this.completionSignals()[key]);
  }

  updateCompletionSignalList(key: string, raw: string): void {
    const next = { ...this.completionSignals() };
    assignOrDelete(next, key, lines(raw));
    this.valueChange.emit(next);
  }

  private updateProcessLifecycle(key: string, value: unknown): void {
    const next = { ...this.processLifecycle() };
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  dataArtifactGroups(): StructuredListGroup[] {
    return DATA_ARTIFACT_GROUPS;
  }

  updateDataArtifactList(key: string, raw: string): void {
    this.updateObjectList(key, raw);
  }

  sourceCoverage(): JsonObject {
    const value = this.value();
    if (Array.isArray(value)) return asObject(value[0]);
    return asObject(value);
  }

  sourceCoverageText(key: string): string {
    return text(this.sourceCoverage()[key]);
  }

  sourceCoverageList(key: string): string[] {
    const coverage = this.sourceCoverage();
    if (key === 'scannedSources' && !coverage[key] && coverage['sources']) return stringList(coverage['sources']);
    return stringList(coverage[key]);
  }

  sourceCoverageStatuses(): string[] {
    return SOURCE_COVERAGE_STATUSES;
  }

  updateSourceCoverage(key: string, value: unknown): void {
    const next = { ...this.sourceCoverage() };
    if (key === 'scannedSources') delete next['sources'];
    assignOrDelete(next, key, value);
    this.valueChange.emit(next);
  }

  updateSourceCoverageList(key: string, raw: string): void {
    this.updateSourceCoverage(key, lines(raw));
  }

  gaps(): JsonObject[] {
    const value = this.value();
    if (!Array.isArray(value)) return value ? [asObject(value)] : [];
    return value.map((item) => item !== null && typeof item === 'object' && !Array.isArray(item)
      ? item as JsonObject
      : { summary: text(item) });
  }

  gapSeverities(): string[] {
    return GAP_SEVERITIES;
  }

  gapStatuses(): string[] {
    return GAP_STATUSES;
  }

  addGap(): void {
    this.valueChange.emit([...this.gaps(), { id: '', type: '', summary: '', severity: 'info', status: 'open', suggestedNextSources: [] }]);
  }

  updateGap(index: number, key: string, value: unknown): void {
    const gaps = this.gaps().map((item) => ({ ...item }));
    assignOrDelete(gaps[index], key, value);
    this.valueChange.emit(gaps);
  }

  updateGapList(index: number, key: string, raw: string): void {
    this.updateGap(index, key, lines(raw));
  }

  removeGap(index: number): void {
    this.valueChange.emit(this.gaps().filter((_, rowIndex) => rowIndex !== index));
  }

  relations(): JsonObject[] {
    return objectList(this.value());
  }

  addRelation(): void {
    const targetType = defaultRelationTargetType(this.entityType());
    this.valueChange.emit([...this.relations(), { type: 'related-to', targetType, target: '' }]);
  }

  updateRelation(index: number, key: string, value: unknown): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    if (!relations[index]) return;
    assignOrDelete(relations[index], key, value);
    this.valueChange.emit(relations);
  }

  removeRelation(index: number): void {
    this.valueChange.emit(this.relations().filter((_, relationIndex) => relationIndex !== index));
  }

  relationTargetTypes(): ReferenceGroup[] {
    return RELATION_TARGET_TYPES;
  }

  relationTargetType(index: number): string {
    const relation = this.relations()[index] || {};
    if (text(relation['targetContextId'])) return 'bounded-context';
    if (text(relation['targetProcessId'])) return 'process';
    const declaredType = normalizeEntityType(text(relation['targetType']));
    return declaredType || (text(relation['target']) ? 'system' : '');
  }

  relationTargetId(index: number): string {
    const relation = this.relations()[index] || {};
    return text(relation['target'] || relation['targetContextId'] || relation['targetProcessId']);
  }

  relationTargetOptions(index: number): OperationalContextReferenceOption[] {
    const type = this.relationTargetType(index) as OperationalContextWritableType;
    if (!RELATION_TARGET_TYPES.some((candidate) => candidate.type === type)) return [];
    return this.optionsFor(type).filter((option) => !(type === this.entityType() && option.id === this.entityId()));
  }

  updateRelationTargetType(index: number, event: Event): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    delete relation['target'];
    delete relation['targetContextId'];
    delete relation['targetProcessId'];
    delete relation['externalSystem'];
    assignOrDelete(relation, 'targetType', eventValue(event));
    this.valueChange.emit(relations);
  }

  updateRelationTarget(index: number, event: Event): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    delete relation['targetContextId'];
    delete relation['targetProcessId'];
    delete relation['externalSystem'];
    assignOrDelete(relation, 'target', eventValue(event));
    this.valueChange.emit(relations);
  }

  updateRelationExternalTarget(index: number, value: string): void {
    const relations = this.relations().map((relation) => ({ ...relation }));
    const relation = relations[index];
    if (!relation) return;
    if (value.trim()) {
      delete relation['targetType'];
      delete relation['target'];
      delete relation['targetContextId'];
      delete relation['targetProcessId'];
    }
    assignOrDelete(relation, 'externalSystem', value.trim());
    this.valueChange.emit(relations);
  }

  selectedRelationVia(index: number): string[] {
    return stringList(this.relations()[index]?.['via']);
  }

  relationViaOptions(index: number): OperationalContextReferenceOption[] {
    const selected = this.selectedRelationVia(index);
    return this.optionsFor('integration').filter((option) =>
      !selected.includes(option.id)
      && !(this.entityType() === 'integration' && option.id === this.entityId())
    );
  }

  addRelationVia(index: number, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateRelation(index, 'via', unique([...this.selectedRelationVia(index), id]));
    resetSelect(event);
  }

  removeRelationVia(index: number, id: string): void {
    this.updateRelation(index, 'via', this.selectedRelationVia(index).filter((value) => value !== id));
  }

  optionLabel(type: OperationalContextWritableType, id: string): string {
    return this.optionsFor(type).find((option) => option.id === id)?.label || id;
  }

  targetType(): string {
    return text(this.object()['type']);
  }

  targetOptions(): OperationalContextReferenceOption[] {
    const type = this.targetType();
    return type === 'system' || type === 'bounded-context' ? this.optionsFor(type) : [];
  }

  updateTargetType(event: Event): void {
    this.valueChange.emit({ ...this.object(), type: eventValue(event), id: '' });
  }

  repositories(): JsonObject[] {
    return objectList(this.value());
  }

  addRepository(): void {
    this.valueChange.emit([
      ...this.repositories(),
      { repoId: '', role: this.repositories().length ? 'supporting' : 'primary', priority: this.repositories().length + 1, searchMode: 'whole-repository' }
    ]);
  }

  updateRepository(index: number, key: string, value: unknown): void {
    const repositories = this.repositories().map((repository) => ({ ...repository }));
    const repository = repositories[index];
    assignOrDelete(repository, key, value);
    if (key === 'searchMode' && value === 'whole-repository') delete repository['pathPrefixes'];
    this.valueChange.emit(repositories);
  }

  updateRepositoryNumber(index: number, key: string, event: Event): void {
    const raw = eventValue(event);
    this.updateRepository(index, key, raw ? Number(raw) : null);
  }

  updateRepositoryList(index: number, key: string, raw: string): void {
    this.updateRepository(index, key, lines(raw));
  }

  removeRepository(index: number): void {
    this.valueChange.emit(this.repositories().filter((_, itemIndex) => itemIndex !== index));
  }

  repositoryOptions(index: number): OperationalContextReferenceOption[] {
    const current = text(this.repositories()[index]?.['repoId']);
    const selected = this.repositories().map((repository) => text(repository['repoId'])).filter(Boolean);
    return this.optionsFor('repository').filter((option) => option.id === current || !selected.includes(option.id));
  }

  processSteps(): JsonObject[] {
    return objectList(this.value());
  }

  addProcessStep(): void {
    this.valueChange.emit([
      ...this.processSteps(),
      { id: '', name: '', type: 'business-step', summary: '', references: {} }
    ]);
  }

  updateProcessStep(index: number, key: string, value: unknown): void {
    const steps = this.processSteps().map((step) => ({ ...step }));
    assignOrDelete(steps[index], key, value);
    this.valueChange.emit(steps);
  }

  removeProcessStep(index: number): void {
    this.valueChange.emit(this.processSteps().filter((_, itemIndex) => itemIndex !== index));
  }

  moveProcessStep(index: number, offset: -1 | 1): void {
    const target = index + offset;
    const steps = this.processSteps().map((step) => ({ ...step }));
    if (target < 0 || target >= steps.length) return;
    [steps[index], steps[target]] = [steps[target], steps[index]];
    this.valueChange.emit(steps);
  }

  processStepReferenceGroups(): ReferenceGroup[] {
    return PROCESS_STEP_REFERENCE_GROUPS;
  }

  selectedProcessStepReferences(index: number, key: string): string[] {
    const references = asObject(this.processSteps()[index]?.['references']);
    return stringList(references[key]);
  }

  availableProcessStepReferences(index: number, referenceGroup: ReferenceGroup): OperationalContextReferenceOption[] {
    const selected = this.selectedProcessStepReferences(index, referenceGroup.key);
    return this.optionsFor(referenceGroup.type).filter((option) => !selected.includes(option.id));
  }

  addProcessStepReference(index: number, referenceGroup: ReferenceGroup, event: Event): void {
    const id = eventValue(event);
    if (!id) return;
    this.updateProcessStepReferenceList(index, referenceGroup.key, unique([
      ...this.selectedProcessStepReferences(index, referenceGroup.key),
      id
    ]));
    resetSelect(event);
  }

  removeProcessStepReference(index: number, key: string, id: string): void {
    this.updateProcessStepReferenceList(
      index,
      key,
      this.selectedProcessStepReferences(index, key).filter((value) => value !== id)
    );
  }

  processStepStrongTerms(index: number): string[] {
    const matchSignals = asObject(this.processSteps()[index]?.['matchSignals']);
    return stringList(asObject(matchSignals['strong'])['terms']);
  }

  updateProcessStepStrongTerms(index: number, raw: string): void {
    const step = this.processSteps()[index] || {};
    const matchSignals = { ...asObject(step['matchSignals']) };
    const strong = { ...asObject(matchSignals['strong']) };
    assignOrDelete(strong, 'terms', lines(raw));
    assignOrDelete(matchSignals, 'strong', strong);
    this.updateProcessStep(index, 'matchSignals', matchSignals);
  }

  private updateProcessStepReferenceList(index: number, key: string, values: string[]): void {
    const step = this.processSteps()[index] || {};
    const references = { ...asObject(step['references']) };
    assignOrDelete(references, key, values);
    this.updateProcessStep(index, 'references', references);
  }

  participant(groupName: ParticipantGroup, index = 0): JsonObject {
    const participants = this.object();
    return groupName === 'source'
      ? asObject(participants['source'])
      : objectList(participants[groupName])[index] || {};
  }

  participantRows(): ParticipantRow[] {
    return [
      { group: 'source', index: 0, label: 'Source', removable: false },
      ...this.participantList('targets').map((_, index) => ({ group: 'targets' as const, index, label: `Target ${index + 1}`, removable: true })),
      ...this.participantList('intermediaries').map((_, index) => ({ group: 'intermediaries' as const, index, label: `Intermediary ${index + 1}`, removable: true })),
      ...this.participantList('finalTargets').map((_, index) => ({ group: 'finalTargets' as const, index, label: `Final target ${index + 1}`, removable: true }))
    ];
  }

  participantList(groupName: Exclude<ParticipantGroup, 'source'>): JsonObject[] {
    return objectList(this.object()[groupName]);
  }

  addParticipant(groupName: Exclude<ParticipantGroup, 'source'>): void {
    const next = { ...this.object(), [groupName]: [...this.participantList(groupName), {}] };
    this.valueChange.emit(next);
  }

  removeParticipant(groupName: Exclude<ParticipantGroup, 'source'>, index: number): void {
    const next = {
      ...this.object(),
      [groupName]: this.participantList(groupName).filter((_, itemIndex) => itemIndex !== index)
    };
    this.valueChange.emit(next);
  }

  updateParticipant(groupName: ParticipantGroup, index: number, key: string, value: unknown): void {
    const next = { ...this.object() };
    if (groupName === 'source') {
      const source = { ...asObject(next['source']) };
      assignOrDelete(source, key, value);
      next['source'] = source;
    } else {
      const participants = this.participantList(groupName).map((participant) => ({ ...participant }));
      assignOrDelete(participants[index], key, value);
      next[groupName] = participants;
    }
    this.valueChange.emit(next);
  }

  updateParticipantList(groupName: ParticipantGroup, index: number, key: string, raw: string): void {
    this.updateParticipant(groupName, index, key, lines(raw));
  }

  private optionsFor(type: OperationalContextWritableType): OperationalContextReferenceOption[] {
    return this.referenceOptions()[type] || [];
  }

  private emitSignals(rows: MatchSignalRow[]): void {
    const source = this.object();
    const tiered = SIGNAL_STRENGTHS.some((strength) => Object.hasOwn(source, strength.key));
    const next: JsonObject = Object.fromEntries(Object.entries(source).filter(([key, value]) =>
      tiered ? !isSignalStrength(key) : value !== null && typeof value === 'object' && !Array.isArray(value)
    ));
    for (const strength of SIGNAL_STRENGTHS) {
      const bucket: JsonObject = {};
      for (const row of rows.filter((candidate) => candidate.strength === strength.key && candidate.key)) {
        bucket[row.key] = unique([...(stringList(bucket[row.key])), ...row.values]);
      }
      if (Object.keys(bucket).length) next[strength.key] = bucket;
    }
    this.valueChange.emit(next);
  }
}

function asObject(value: unknown): JsonObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as JsonObject : {};
}

function objectList(value: unknown): JsonObject[] {
  return Array.isArray(value) ? value.map(asObject) : [];
}

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value);
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.map(text).filter(Boolean) : [];
}

function legacyTextList(value: unknown): string[] {
  if (Array.isArray(value)) return stringList(value);
  const item = text(value).trim();
  return item ? [item] : [];
}

function lines(value: string): string[] {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function isSignalStrength(value: string): value is SignalStrength {
  return SIGNAL_STRENGTHS.some((strength) => strength.key === value);
}

function normalizeEntityType(value: string): string {
  const normalized = value.trim().replaceAll('_', '-');
  const aliases: Record<string, string> = {
    systems: 'system', repositories: 'repository', processes: 'process', integrations: 'integration', teams: 'team',
    boundedcontext: 'bounded-context', boundedcontexts: 'bounded-context', 'bounded-contexts': 'bounded-context',
    codesearchscope: 'code-search-scope', codesearchscopes: 'code-search-scope', 'code-search-scopes': 'code-search-scope',
    terms: 'glossary-term', 'glossary-terms': 'glossary-term', handoffrules: 'handoff-rule', 'handoff-rules': 'handoff-rule'
  };
  return aliases[normalized.toLowerCase()] || normalized;
}

function defaultRelationTargetType(sourceType: OperationalContextWritableType): OperationalContextWritableType {
  if (sourceType === 'process') return 'process';
  if (sourceType === 'integration') return 'process';
  if (sourceType === 'bounded-context') return 'bounded-context';
  return 'system';
}

function assignOrDelete(target: JsonObject, key: string, value: unknown): void {
  const emptyArray = Array.isArray(value) && value.length === 0;
  const emptyObject = value !== null && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0;
  if (value === '' || value === null || value === undefined || emptyArray || emptyObject) delete target[key];
  else target[key] = value;
}

function eventValue(event: Event): string {
  return String((event.target as HTMLInputElement | HTMLSelectElement | null)?.value || '').trim();
}

function resetSelect(event: Event): void {
  const select = event.target as HTMLSelectElement | null;
  if (select) select.value = '';
}
