import { OPERATIONAL_CONTEXT_WRITABLE_TYPES, OperationalContextWritableType } from '../../models/operational-context-maintenance.models';
import { OperationalContextFormAdapter, operationalContextFieldTooltip } from './operational-context-form-adapter';

describe('OperationalContextFormAdapter', () => {
  const adapter = new OperationalContextFormAdapter();

  it.each(OPERATIONAL_CONTEXT_WRITABLE_TYPES)('round-trips the complete anonymized CRM payload for %s', (type) => {
    const payload = crmPayload(type);
    const form = adapter.build(type, payload);
    expect(adapter.payload(type, form, payload)).toEqual(payload);
  });

  it('maps a backend JSON Pointer to the owning structured CRM control', () => {
    expect(adapter.fieldForPointer('process', '/payload/steps/0/references/systems/0')?.path).toBe('steps');
    expect(adapter.fieldForPointer('system', '/payload/relations/0/target')?.path).toBe('relations');
    expect(adapter.fieldForPointer('system', '/payload/matchSignals/strong/routes/0')?.path).toBe('matchSignals');
    expect(adapter.fieldForPointer('process', '/payload/failureModes/0/affectedStep')?.path).toBe('failureModes');
    expect(adapter.fieldForPointer('process', '/payload/dataAndArtifacts/outputArtifacts/0')?.path).toBe('dataAndArtifacts');
    expect(adapter.fieldForPointer('process', '/payload/processBoundary/endsWhen/0')?.path).toBe('processBoundary');
    expect(adapter.fieldForPointer('process', '/payload/lifecycle/transitions/0/to')?.path).toBe('lifecycle');
    expect(adapter.fieldForPointer('process', '/payload/completionSignals/successful/0')?.path).toBe('completionSignals');
    expect(adapter.fieldForPointer('repository', '/payload/sourceCoverage/limitations/0')?.path).toBe('sourceCoverage');
    expect(adapter.fieldForPointer('system', '/payload/runtime/configurationDirectory')?.path).toBe('runtime');
    expect(adapter.fieldForPointer('repository', '/payload/evidence/0/sourceRef')?.path).toBe('evidence');
    expect(adapter.fieldForPointer('repository', '/payload/llmToolHints/disambiguateFrom/0')?.path).toBe('llmToolHints');
    expect(adapter.fieldForPointer('bounded-context', '/payload/scope/includes/0')?.path).toBe('scope');
    expect(adapter.fieldForPointer('bounded-context', '/payload/semanticBoundary/invariants/0')?.path).toBe('semanticBoundary');
    expect(adapter.fieldForPointer('bounded-context', '/payload/evidence/0/evidenceType')?.path).toBe('evidence');
    expect(adapter.fieldForPointer('bounded-context', '/payload/gaps/0/summary')?.path).toBe('gaps');
    expect(adapter.fieldForPointer('system', '/payload/name')?.path).toBe('name');
  });

  it('preserves unrecognized canonical fields during an edit', () => {
    const payload = { id: 'crm-contact-core', name: 'CRM Contact Core', futureCrmAttribute: { enabled: true } };
    const form = adapter.build('system', payload);
    form.controls['name'].setValue('CRM Contact Platform');
    expect(adapter.payload('system', form, payload)['futureCrmAttribute']).toEqual({ enabled: true });
  });

  it.each(OPERATIONAL_CONTEXT_WRITABLE_TYPES)('provides complete runtime and AI guidance for every anonymized CRM %s input', (type) => {
    const fields = adapter.fields(type);
    expect(fields.length).toBeGreaterThan(0);
    for (const field of fields) {
      expect(field.guidance.whatToEnter).toBeTruthy();
      expect(field.guidance.runtimeEffect).toBeTruthy();
      expect(field.guidance.acceptedValues).toBeTruthy();
      expect(field.guidance.example).toBeTruthy();
      expect(operationalContextFieldTooltip(field)).toContain('Runtime / AI effect:');
      expect(operationalContextFieldTooltip(field)).toContain('Format / values:');
      expect(operationalContextFieldTooltip(field)).toContain('CRM example:');
    }
  });

  it('does not expose a raw JSON field for any supported anonymized CRM entity', () => {
    const kinds = OPERATIONAL_CONTEXT_WRITABLE_TYPES.flatMap((type) => adapter.fields(type).map((field) => field.kind));
    expect(kinds).not.toContain('json');
  });

  it('documents strict CRM code-search target and repository constraints', () => {
    const target = adapter.fields('code-search-scope').find((field) => field.path === 'target');
    const repositories = adapter.fields('code-search-scope').find((field) => field.path === 'repositories');
    expect(target?.guidance.acceptedValues).toContain('strictly system or bounded-context');
    expect(repositories?.guidance.acceptedValues).toContain('whole-repository or path-prefixes');
    expect(repositories?.guidance.acceptedValues).toContain('positive');
  });

  it('exposes lifecycle status for every canonical CRM entity that supports the field', () => {
    for (const type of OPERATIONAL_CONTEXT_WRITABLE_TYPES.filter((type) => type !== 'handoff-rule')) {
      expect(adapter.fields(type).some((field) => field.path === 'lifecycleStatus')).toBe(true);
    }
  });

  it('does not expose unsupported common CRM fields for a code-search scope', () => {
    const paths = adapter.fields('code-search-scope').map((field) => field.path);
    expect(paths).not.toContain('shortName');
    expect(paths).not.toContain('purpose');
    expect(paths).not.toContain('aliases');
  });

  it('uses structured controls for the highest-risk CRM maintenance fields', () => {
    const systemSubtype = adapter.fields('system').find((field) => field.path === 'systemSubtype');
    expect(systemSubtype?.kind).toBe('select');
    expect(systemSubtype?.choices).toEqual(['frontend', 'backend', 'worker', 'mixed', 'unknown']);
    expect(adapter.fields('system').find((field) => field.path === 'ownership')?.kind).toBe('ownership');
    expect(adapter.fields('system').find((field) => field.path === 'participants')?.kind).toBe('system-participants');
    expect(adapter.fields('system').find((field) => field.path === 'runtime')?.kind).toBe('system-runtime');
    expect(adapter.fields('repository').find((field) => field.path === 'evidence')?.kind).toBe('repository-evidence');
    expect(adapter.fields('repository').find((field) => field.path === 'llmToolHints')?.kind).toBe('repository-llm-tool-hints');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'localLanguageSummary')?.kind).toBe('bounded-local-language');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'scope')?.kind).toBe('bounded-scope');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'semanticBoundary')?.kind).toBe('bounded-semantic-boundary');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'evidence')?.kind).toBe('bounded-evidence');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'llmToolHints')?.kind).toBe('bounded-llm-tool-hints');
    expect(adapter.fields('process').find((field) => field.path === 'references')?.kind).toBe('references');
    expect(adapter.fields('code-search-scope').find((field) => field.path === 'target')?.kind).toBe('code-search-target');
    expect(adapter.fields('code-search-scope').find((field) => field.path === 'repositories')?.kind).toBe('code-search-repositories');
    expect(adapter.fields('integration').find((field) => field.path === 'participants')?.kind).toBe('integration-participants');
    expect(adapter.fields('repository').find((field) => field.path === 'git')?.kind).toBe('repository-git');
    expect(adapter.fields('process').find((field) => field.path === 'participants')?.kind).toBe('process-participants');
    expect(adapter.fields('process').find((field) => field.path === 'steps')?.kind).toBe('process-steps');
    expect(adapter.fields('process').find((field) => field.path === 'processBoundary')?.kind).toBe('process-boundary');
    expect(adapter.fields('process').find((field) => field.path === 'lifecycle')?.kind).toBe('process-lifecycle');
    expect(adapter.fields('process').find((field) => field.path === 'completionSignals')?.kind).toBe('completion-signals');
    expect(adapter.fields('system').find((field) => field.path === 'matchSignals')?.kind).toBe('match-signals');
    expect(adapter.fields('system').find((field) => field.path === 'relations')?.kind).toBe('relations');
    expect(adapter.fields('process').find((field) => field.path === 'failureModes')?.kind).toBe('failure-modes');
    expect(adapter.fields('integration').find((field) => field.path === 'failureModes')?.kind).toBe('failure-modes');
    expect(adapter.fields('process').find((field) => field.path === 'dataAndArtifacts')?.kind).toBe('data-artifacts');
    expect(adapter.fields('repository').find((field) => field.path === 'sourceCoverage')?.kind).toBe('source-coverage');
    expect(adapter.fields('bounded-context').find((field) => field.path === 'gaps')?.kind).toBe('catalog-gaps');
  });

  it('uses the Recognition signals label for the canonical matchSignals payload', () => {
    for (const type of OPERATIONAL_CONTEXT_WRITABLE_TYPES) {
      const field = adapter.fields(type).find((field) => field.path === 'matchSignals');
      if (field) {
        expect(field.label).toBe('Recognition signals');
      }
    }
  });

  it('does not send server-owned CRM Git, participant repository or legacy step fields back to maintenance API', () => {
    const repository = { id: 'crm-contact-repository', name: 'CRM Contact Repository', git: { projectPath: 'crm/contact-service', inferred: true } };
    const repositoryForm = adapter.build('repository', repository);
    expect(adapter.payload('repository', repositoryForm, repository)['git']).toEqual({ projectPath: 'crm/contact-service' });

    const process = {
      id: 'crm-contact-update',
      name: 'CRM Contact Update',
      steps: [{ id: 'accept-update', name: 'Accept update', match: { routes: ['/crm/contacts'] }, futureCrmStepField: true }]
    };
    const processForm = adapter.build('process', process);
    expect(adapter.payload('process', processForm, process)['steps']).toEqual([
      { id: 'accept-update', name: 'Accept update', futureCrmStepField: true }
    ]);

    const integration = {
      id: 'crm-contact-sync',
      name: 'CRM Contact Sync',
      participants: {
        source: { system: 'crm-contact-core', repositories: ['crm-contact-repository'], futureCrmParticipantField: true },
        targets: [{ system: 'crm-profile-store', repositories: ['crm-profile-repository'] }]
      }
    };
    const integrationForm = adapter.build('integration', integration);
    expect(adapter.payload('integration', integrationForm, integration)['participants']).toEqual({
      source: { system: 'crm-contact-core', futureCrmParticipantField: true },
      targets: [{ system: 'crm-profile-store' }]
    });
  });

  it('derives CRM code-search scopeType from the governed target selector', () => {
    const payload = {
      id: 'crm-contact-code-scope',
      name: 'CRM Contact code scope',
      scopeType: 'bounded-context',
      target: { type: 'system', id: 'crm-contact-core' },
      repositories: [{ repoId: 'crm-contact-repository', role: 'primary', priority: 1, searchMode: 'whole-repository' }]
    };
    const form = adapter.build('code-search-scope', payload);
    expect(adapter.payload('code-search-scope', form, payload)['scopeType']).toBe('system');
    expect(adapter.fields('code-search-scope').some((field) => field.path === 'scopeType')).toBe(false);
  });
});

function crmPayload(type: OperationalContextWritableType): Record<string, unknown> {
  const base = { id: `crm-${type}-01`, name: `CRM ${type}`, lifecycleStatus: 'active', summary: 'Anonymized CRM catalogue example.', aliases: ['crm-example'] };
  switch (type) {
    case 'system': return { ...base, systemType: 'internal-service', systemSubtype: 'frontend', participants: { externalOwner: 'CRM platform provider' }, runtime: { configurationDirectory: 'crm/contact-service' }, ownership: { ownerTeamIds: ['crm-domain-team'], ownershipStatus: 'explicit', confidence: 'high' }, matchSignals: { strong: { routes: ['/crm/contacts'] } }, sourceCoverage: { status: 'partial', scannedSources: ['Anonymized CRM architecture notes'], limitations: ['CRM retry ownership is not confirmed.'] }, gaps: [{ id: 'crm-retry-owner', type: 'unconfirmed-ownership', summary: 'Confirm CRM retry ownership.', severity: 'warning', status: 'open' }] };
    case 'repository': return { ...base, repositoryType: 'application', git: { projectPath: 'crm/contact-service' }, references: { systems: ['crm-system-01'] }, evidence: [{ sourceRef: 'crm/contact-service/pom.xml', evidenceType: 'build-definition', note: 'Anonymized CRM service module.' }], llmToolHints: { answerWhenUserMentions: ['CRM contact validation'], disambiguateFrom: ['CRM authentication account service'] }, sourceCoverage: { status: 'partial', scannedSources: ['CRM contact module'], expectedSources: ['CRM migration module'] } };
    case 'code-search-scope': return { id: 'crm-code-search-scope-01', name: 'CRM code search scope', lifecycleStatus: 'active', summary: 'Anonymized CRM code boundary.', useFor: ['Inspect CRM contact behavior.'], scopeType: 'system', target: { type: 'system', id: 'crm-system-01' }, repositories: [{ repoId: 'crm-repository-01', role: 'primary', priority: 1, searchMode: 'path-prefixes', pathPrefixes: ['apps/crm-contact'] }] };
    case 'process': return { ...base, type: 'business', participants: { primarySystems: ['crm-system-01'] }, steps: [{ id: 'capture-contact', name: 'Capture CRM contact', references: { systems: ['crm-system-01'] } }], processBoundary: { businessCapability: 'CRM Contact Preference Management', startsWhen: ['An anonymized CRM contact update is accepted.'], endsWhen: ['The CRM contact view confirms the update.'], includes: ['CRM contact preference validation'], excludes: ['Authentication credential lifecycle'], assumptions: ['CRM contact identity is already resolved.'], futureCrmBoundaryHint: true }, lifecycle: { triggers: [{ type: 'api', name: 'CRM contact update' }], entryCriteria: ['CRM contact identity is available.'], statuses: ['requested', 'applied'], transitions: [{ from: 'requested', to: 'applied', trigger: 'CRM contact validation succeeds.' }], terminalStates: ['applied'], successOutcomes: ['CRM contact preference is applied.'], partialOutcomes: ['CRM projection is pending.'], failedOutcomes: ['CRM validation rejects the update.'], cancellationOutcomes: ['CRM agent cancels the update.'], futureCrmLifecycleHint: true }, completionSignals: { successful: ['CRM contact confirmation is recorded.'], partial: ['CRM projection remains pending.'], failed: ['CRM validation rejection is recorded.'], cancelled: ['CRM cancellation is recorded.'], futureCrmCompletionHint: true }, failureModes: [{ id: 'crm-contact-rejected', name: 'CRM contact rejected', summary: 'The anonymized CRM contact change is rejected.', affectedStep: 'capture-contact', signals: ['CRM validation rejection'] }], dataAndArtifacts: { primaryObjects: ['ContactPreference'], inputArtifacts: ['Anonymized CRM contact change request'], outputArtifacts: ['CRM contact update confirmation'] } };
    case 'integration': return { ...base, category: 'internal-api', participants: { source: { system: 'crm-system-01' }, targets: [{ system: 'crm-system-02' }] }, failureModes: [{ name: 'CRM boundary timeout', type: 'timeout', symptom: 'The CRM response exceeds the agreed window.', impact: 'The contact update cannot continue.' }] };
    case 'bounded-context': return { ...base, type: 'core-domain', localLanguageSummary: ['In CRM, contact means the communication profile, not an authentication account.'], ownership: { ownerTeamIds: ['crm-domain-team'], ownershipStatus: 'explicit', confidence: 'high' }, scope: { includes: ['CRM contact preference validation'], excludes: ['Authentication credential lifecycle'], businessCapabilities: ['CRM Contact Preference Management'], coreEntities: ['ContactPreference'], keyDecisions: ['Whether an anonymized contact preference is valid.'] }, semanticBoundary: { coreConcepts: ['Contact preference'], localConcepts: ['CRM contact profile'], canonicalEntities: ['ContactPreference'], commands: ['UpdateContactPreference'], events: ['ContactPreferenceUpdated'], invariants: ['A CRM preference belongs to one anonymized contact profile.'], ownsLanguage: ['CRM contact preference'], doesNotOwn: ['Authentication account credential'] }, evidence: [{ sourceRef: 'Anonymized CRM domain glossary', evidenceType: 'domain-documentation', note: 'CRM semantic boundary review.' }], llmToolHints: { answerWhenUserMentions: ['CRM contact preference'], disambiguateFrom: ['Authentication account'], usefulSearchKeywords: ['ContactPreference'], explanationStyle: 'Explain as the CRM contact-preference boundary.' }, sourceCoverage: { status: 'partial', scannedSources: ['Anonymized CRM domain notes'] }, gaps: [{ id: 'crm-consent-boundary', type: 'unresolved-boundary', summary: 'Confirm the CRM consent boundary.', severity: 'info', status: 'open' }] };
    case 'team': return { ...base, type: 'product', matchSignals: { aliases: ['crm-domain-team'] } };
    case 'glossary-term': return { id: 'crm-customer-profile', term: 'Customer profile', category: 'domain-term', lifecycleStatus: 'active', definition: 'An anonymized CRM customer profile.', localMeaningAndBoundaries: ['Represents the CRM view of a customer.'], aliases: ['CRM profile'], useFor: ['case-routing'], matchSignals: { exact: { alias: ['customer profile'] } }, canonicalReferences: ['system:crm-system-01'], relatedTerms: ['crm-contact-preference'], doNotConfuseWith: ['Authentication account'], responsibilityHints: ['Resolve ownership through the CRM customer context.'], llmToolHints: ['Use for CRM terminology only.'], notes: ['Anonymized CRM fixture.'] };
    case 'handoff-rule': return { id: 'crm-contact-sync-delayed', title: 'CRM contact synchronization is delayed', confidence: 'medium', useWhen: ['A CRM contact update is not visible downstream.'], doNotUseWhen: ['The update is still inside its documented processing window.'], requiredEvidence: ['Anonymized CRM correlation key.'], expectedFirstAction: ['Verify the CRM synchronization boundary.'], references: { systems: ['crm-system-01'], terms: ['crm-customer-profile'] }, affectedSystems: ['system:crm-system-01'], affectedProcesses: ['process:crm-contact-update'], affectedIntegrations: ['integration:crm-contact-sync'], notes: ['Anonymized CRM fixture.'], llmToolHints: ['Collect evidence from both CRM sides.'], limitations: ['No production identifiers.'] };
  }
}
