import { TestBed } from '@angular/core/testing';

import { OperationalContextWritableType } from '../../models/operational-context-maintenance.models';
import { OperationalContextFormAdapter } from '../context-entity-editor-drawer/operational-context-form-adapter';
import { ContextStructuredFieldEditorComponent } from './context-structured-field-editor';

describe('ContextStructuredFieldEditorComponent', () => {
  const adapter = new OperationalContextFormAdapter();

  it('offers only the governed CRM target types and matching catalogue entities', async () => {
    const fixture = await createFixture('code-search-scope', 'target', { type: 'system', id: 'crm-contact-core' });
    fixture.componentRef.setInput('referenceOptions', {
      system: [{ id: 'crm-contact-core', label: 'CRM Contact Core' }],
      'bounded-context': [{ id: 'crm-customer-context', label: 'CRM Customer Context' }]
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.targetType()).toBe('system');
    expect(fixture.componentInstance.targetOptions()).toEqual([{ id: 'crm-contact-core', label: 'CRM Contact Core' }]);
    const targetTypeOptions = Array.from(fixture.nativeElement.querySelectorAll('#code-target-type option')) as Element[];
    expect(targetTypeOptions.map((option) => option.getAttribute('value'))).toEqual(['', 'system', 'bounded-context']);
  });

  it('builds anonymized CRM repository rows without asking for JSON', async () => {
    const fixture = await createFixture('code-search-scope', 'repositories', []);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.addRepository();

    expect(emitted).toHaveBeenLastCalledWith([expect.objectContaining({ role: 'primary', priority: 1, searchMode: 'whole-repository' })]);

    fixture.componentRef.setInput('value', [{ repoId: 'crm-contact-repository', role: 'primary', priority: 1, searchMode: 'path-prefixes', pathPrefixes: ['apps/crm-contact'] }]);
    fixture.detectChanges();
    fixture.componentInstance.updateRepository(0, 'searchMode', 'whole-repository');
    expect(emitted).toHaveBeenLastCalledWith([{ repoId: 'crm-contact-repository', role: 'primary', priority: 1, searchMode: 'whole-repository' }]);
  });

  it('preserves unknown CRM ownership extensions while editing canonical controls', async () => {
    const fixture = await createFixture('system', 'ownership', {
      ownerTeamIds: ['crm-domain-team'],
      ownershipStatus: 'explicit',
      futureCrmOwnershipEvidence: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.updateObject('confidence', 'high');

    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({
      ownerTeamIds: ['crm-domain-team'],
      confidence: 'high',
      futureCrmOwnershipEvidence: { reviewed: true }
    }));
  });

  it('edits the external CRM system owner without losing participant extensions', async () => {
    const fixture = await createFixture('system', 'participants', {
      externalOwner: 'CRM platform provider',
      futureCrmParticipantHint: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateObject('externalOwner', 'CRM managed platform provider');
    expect(emitted).toHaveBeenLastCalledWith({
      externalOwner: 'CRM managed platform provider',
      futureCrmParticipantHint: { reviewed: true }
    });
    expect(fixture.nativeElement.querySelector('#system-external-owner')).toBeTruthy();
  });

  it('edits the CRM configuration directory without losing runtime extensions', async () => {
    const fixture = await createFixture('system', 'runtime', {
      configurationDirectory: 'crm/contact-service',
      futureCrmRuntimeHint: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateObject('configurationDirectory', 'crm/contact-platform');
    expect(emitted).toHaveBeenLastCalledWith({
      configurationDirectory: 'crm/contact-platform',
      futureCrmRuntimeHint: { reviewed: true }
    });
    expect(fixture.nativeElement.querySelector('#runtime-configuration-directory')).toBeTruthy();
  });

  it('edits guided CRM repository evidence while preserving card extensions', async () => {
    const fixture = await createFixture('repository', 'evidence', [{
      sourceRef: 'crm/contact-service/pom.xml',
      evidenceType: 'build-definition',
      futureCrmEvidenceHint: true
    }]);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateRepositoryEvidence(0, 'note', 'Anonymized CRM service module.');
    expect(emitted).toHaveBeenLastCalledWith([expect.objectContaining({
      sourceRef: 'crm/contact-service/pom.xml',
      evidenceType: 'build-definition',
      note: 'Anonymized CRM service module.',
      futureCrmEvidenceHint: true
    })]);
    expect(fixture.nativeElement.querySelector('#repository-evidence-source-0')).toBeTruthy();
  });

  it('edits guided CRM repository exploration phrases while preserving extensions', async () => {
    const fixture = await createFixture('repository', 'llmToolHints', {
      answerWhenUserMentions: ['CRM contact validation'],
      futureCrmToolHint: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateObjectList('disambiguateFrom', 'CRM authentication account service');
    expect(emitted).toHaveBeenLastCalledWith({
      answerWhenUserMentions: ['CRM contact validation'],
      disambiguateFrom: ['CRM authentication account service'],
      futureCrmToolHint: { reviewed: true }
    });
    expect(fixture.nativeElement.querySelector('#repository-answer-when-mentioned')).toBeTruthy();
  });

  it('normalizes legacy CRM local-language text only after guided editing', async () => {
    const fixture = await createFixture(
      'bounded-context',
      'localLanguageSummary',
      'In CRM, contact means the communication profile, not an authentication account.'
    );
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.boundedLocalLanguage()).toEqual([
      'In CRM, contact means the communication profile, not an authentication account.'
    ]);
    fixture.componentInstance.updateBoundedLocalLanguage(
      'CRM contact means a communication profile.\nCRM account means authentication identity.'
    );
    expect(emitted).toHaveBeenLastCalledWith([
      'CRM contact means a communication profile.',
      'CRM account means authentication identity.'
    ]);
    expect(fixture.nativeElement.querySelector('#bounded-local-language')).toBeTruthy();
  });

  it('edits guided CRM scope and semantic lists without losing extensions', async () => {
    const scopeFixture = await createFixture('bounded-context', 'scope', {
      includes: ['CRM contact preference validation'],
      futureCrmScopeHint: { reviewed: true }
    });
    const scopeEmitted = vi.fn();
    scopeFixture.componentInstance.valueChange.subscribe(scopeEmitted);
    scopeFixture.componentInstance.updateObjectList('excludes', 'Authentication credential lifecycle');
    expect(scopeEmitted).toHaveBeenLastCalledWith({
      includes: ['CRM contact preference validation'],
      excludes: ['Authentication credential lifecycle'],
      futureCrmScopeHint: { reviewed: true }
    });

    TestBed.resetTestingModule();
    const semanticFixture = await createFixture('bounded-context', 'semanticBoundary', {
      ownsLanguage: ['CRM contact preference'],
      futureCrmSemanticHint: { reviewed: true }
    });
    const semanticEmitted = vi.fn();
    semanticFixture.componentInstance.valueChange.subscribe(semanticEmitted);
    semanticFixture.componentInstance.updateObjectList('doesNotOwn', 'Authentication account credential');
    expect(semanticEmitted).toHaveBeenLastCalledWith({
      ownsLanguage: ['CRM contact preference'],
      doesNotOwn: ['Authentication account credential'],
      futureCrmSemanticHint: { reviewed: true }
    });
    expect(scopeFixture.nativeElement.querySelector('#bounded-scope-includes')).toBeTruthy();
    expect(semanticFixture.nativeElement.querySelector('#bounded-semantic-ownsLanguage')).toBeTruthy();
  });

  it('edits guided CRM bounded-context evidence and AI hints while preserving extensions', async () => {
    const evidenceFixture = await createFixture('bounded-context', 'evidence', [{
      sourceRef: 'Anonymized CRM domain glossary',
      evidenceType: 'domain-documentation',
      futureCrmEvidenceHint: true
    }]);
    const evidenceEmitted = vi.fn();
    evidenceFixture.componentInstance.valueChange.subscribe(evidenceEmitted);
    evidenceFixture.componentInstance.updateBoundedEvidence(0, 'note', 'CRM semantic boundary review.');
    expect(evidenceEmitted).toHaveBeenLastCalledWith([expect.objectContaining({
      note: 'CRM semantic boundary review.',
      futureCrmEvidenceHint: true
    })]);

    TestBed.resetTestingModule();
    const hintsFixture = await createFixture('bounded-context', 'llmToolHints', {
      answerWhenUserMentions: ['CRM contact preference'],
      futureCrmToolHint: { reviewed: true }
    });
    const hintsEmitted = vi.fn();
    hintsFixture.componentInstance.valueChange.subscribe(hintsEmitted);
    hintsFixture.componentInstance.updateObject('explanationStyle', 'Explain as the CRM preference boundary.');
    expect(hintsEmitted).toHaveBeenLastCalledWith({
      answerWhenUserMentions: ['CRM contact preference'],
      explanationStyle: 'Explain as the CRM preference boundary.',
      futureCrmToolHint: { reviewed: true }
    });
    expect(evidenceFixture.nativeElement.querySelector('#bounded-evidence-source-0')).toBeTruthy();
    expect(hintsFixture.nativeElement.querySelector('#bounded-explanation-style')).toBeTruthy();
  });

  it('uses per-type CRM reference groups and excludes self references', async () => {
    const fixture = await createFixture('process', 'references', { systems: ['crm-contact-core'] }, 'crm-contact-update');
    fixture.componentRef.setInput('referenceOptions', {
      system: [{ id: 'crm-contact-core', label: 'CRM Contact Core' }],
      process: [
        { id: 'crm-contact-update', label: 'CRM Contact Update' },
        { id: 'crm-contact-sync', label: 'CRM Contact Synchronization' }
      ]
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.referenceGroups().map((group) => group.key)).toEqual([
      'systems', 'repositories', 'boundedContexts', 'integrations', 'terms', 'handoffRules'
    ]);
    expect(fixture.componentInstance.availableReferences({ key: 'processes', label: 'Processes', type: 'process' }))
      .toEqual([{ id: 'crm-contact-sync', label: 'CRM Contact Synchronization' }]);
  });

  it('edits CRM integration participant cards while preserving participant extensions', async () => {
    const fixture = await createFixture('integration', 'participants', {
      source: { system: 'crm-contact-core', repositories: ['crm-contact-repository'], futureCrmParticipantHint: 'preserve' },
      targets: [{ system: 'crm-profile-store' }]
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.updateParticipant('source', 0, 'role', 'client');

    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({
      source: { system: 'crm-contact-core', role: 'client', repositories: ['crm-contact-repository'], futureCrmParticipantHint: 'preserve' },
      targets: [{ system: 'crm-profile-store' }]
    }));
    expect(fixture.nativeElement.querySelector('[id^="participant-repo-"]')).toBeNull();
  });

  it('guides CRM match signals by confidence and entity-specific key while preserving extensions', async () => {
    const fixture = await createFixture('system', 'matchSignals', {
      exact: { serviceNames: ['crm-contact-service'] },
      strong: { routes: ['/crm/contacts'] },
      futureCrmSignalMetadata: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.signalRows()).toEqual([
      { strength: 'exact', key: 'serviceNames', values: ['crm-contact-service'] },
      { strength: 'strong', key: 'routes', values: ['/crm/contacts'] }
    ]);
    expect(fixture.componentInstance.signalKeySuggestions()).toContain('serviceNames');
    expect(fixture.componentInstance.signalKeySuggestions()).not.toContain('emailAliases');

    fixture.componentInstance.updateSignalValues(1, '/crm/contacts\n/crm/contact-preferences');
    expect(emitted).toHaveBeenLastCalledWith({
      futureCrmSignalMetadata: { reviewed: true },
      exact: { serviceNames: ['crm-contact-service'] },
      strong: { routes: ['/crm/contacts', '/crm/contact-preferences'] }
    });
  });

  it('reads legacy CRM signals as strong rows and writes the guided tiered shape', async () => {
    const fixture = await createFixture('team', 'matchSignals', {
      emailAliases: ['crm-team@example.invalid'],
      futureCrmSignalMetadata: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.signalRows()).toEqual([
      { strength: 'strong', key: 'emailAliases', values: ['crm-team@example.invalid'] }
    ]);
    fixture.componentInstance.updateSignalValues(0, 'crm-team@example.invalid\ncrm-operations@example.invalid');
    expect(emitted).toHaveBeenLastCalledWith({
      futureCrmSignalMetadata: { reviewed: true },
      strong: { emailAliases: ['crm-team@example.invalid', 'crm-operations@example.invalid'] }
    });
  });

  it('guides canonical CRM relations, recognizes legacy targets and excludes self references', async () => {
    const fixture = await createFixture('bounded-context', 'relations', [{
      type: 'hands-off-to',
      targetContextId: 'crm-engagement-context',
      relationship: 'Anonymized CRM handoff',
      futureCrmRelationHint: 'preserve'
    }], 'crm-customer-context');
    fixture.componentRef.setInput('referenceOptions', {
      'bounded-context': [
        { id: 'crm-customer-context', label: 'CRM Customer Context' },
        { id: 'crm-engagement-context', label: 'CRM Engagement Context' }
      ],
      process: [{ id: 'crm-contact-update', label: 'CRM Contact Update' }]
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.relationTargetType(0)).toBe('bounded-context');
    expect(fixture.componentInstance.relationTargetId(0)).toBe('crm-engagement-context');
    expect(fixture.componentInstance.relationTargetOptions(0).map((option) => option.id))
      .toEqual(['crm-engagement-context']);

    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.updateRelationTargetType(0, { target: { value: 'process' } } as unknown as Event);
    expect(emitted).toHaveBeenLastCalledWith([expect.objectContaining({
      type: 'hands-off-to',
      targetType: 'process',
      relationship: 'Anonymized CRM handoff',
      futureCrmRelationHint: 'preserve'
    })]);
    expect(emitted.mock.calls.at(-1)?.[0][0]).not.toHaveProperty('targetContextId');
  });

  it('edits structured CRM process failure modes and preserves unknown extensions', async () => {
    const fixture = await createFixture('process', 'failureModes', [{
      id: 'crm-contact-rejected',
      name: 'CRM contact rejected',
      summary: 'The anonymized CRM contact update is rejected.',
      affectedStep: 'validate-contact',
      signals: ['CRM validation rejection'],
      futureCrmFailureHint: { reviewed: true }
    }]);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateFailureModeList(0, 'signals', 'CRM validation rejection\nCRM update not persisted');
    expect(emitted).toHaveBeenLastCalledWith([expect.objectContaining({
      id: 'crm-contact-rejected',
      signals: ['CRM validation rejection', 'CRM update not persisted'],
      futureCrmFailureHint: { reviewed: true }
    })]);
    expect(fixture.nativeElement.querySelector('#process-failure-id-0')).toBeTruthy();
  });

  it('normalizes a legacy CRM integration failure description into guided fields', async () => {
    const fixture = await createFixture('integration', 'failureModes', ['CRM profile response timeout']);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.failureModes()).toEqual([{
      name: 'CRM profile response timeout', symptom: 'CRM profile response timeout'
    }]);
    fixture.componentInstance.updateFailureMode(0, 'type', 'timeout');
    expect(emitted).toHaveBeenLastCalledWith([{
      name: 'CRM profile response timeout', symptom: 'CRM profile response timeout', type: 'timeout'
    }]);
  });

  it('edits guided CRM data artifacts while preserving future categories', async () => {
    const fixture = await createFixture('process', 'dataAndArtifacts', {
      inputArtifacts: ['Anonymized CRM contact change request'],
      futureCrmArtifactCategory: ['CRM-derived artifact metadata']
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateDataArtifactList('outputArtifacts', 'CRM contact update confirmation');
    expect(emitted).toHaveBeenLastCalledWith({
      inputArtifacts: ['Anonymized CRM contact change request'],
      outputArtifacts: ['CRM contact update confirmation'],
      futureCrmArtifactCategory: ['CRM-derived artifact metadata']
    });
  });

  it('reads legacy CRM source coverage and writes the canonical guided object', async () => {
    const fixture = await createFixture('bounded-context', 'sourceCoverage', [{
      status: 'partial',
      sources: ['Anonymized CRM domain notes'],
      limitations: ['CRM consent boundary not reviewed'],
      futureCrmCoverageHint: true
    }]);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.sourceCoverageList('scannedSources')).toEqual(['Anonymized CRM domain notes']);
    fixture.componentInstance.updateSourceCoverageList('scannedSources', 'Anonymized CRM domain notes\nCRM glossary review');
    expect(emitted).toHaveBeenLastCalledWith({
      status: 'partial',
      scannedSources: ['Anonymized CRM domain notes', 'CRM glossary review'],
      limitations: ['CRM consent boundary not reviewed'],
      futureCrmCoverageHint: true
    });
  });

  it('edits actionable CRM gap cards without losing future fields', async () => {
    const fixture = await createFixture('system', 'gaps', [{
      id: 'crm-retry-owner',
      type: 'unconfirmed-ownership',
      summary: 'Confirm CRM retry ownership.',
      severity: 'warning',
      status: 'open',
      futureCrmGapHint: 'preserve'
    }]);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateGapList(0, 'suggestedNextSources', 'Anonymized CRM operations notes');
    expect(emitted).toHaveBeenLastCalledWith([expect.objectContaining({
      id: 'crm-retry-owner',
      suggestedNextSources: ['Anonymized CRM operations notes'],
      futureCrmGapHint: 'preserve'
    })]);
    expect(fixture.nativeElement.querySelector('#gap-summary-0')).toBeTruthy();
  });

  it('edits the guided CRM process boundary while preserving extensions', async () => {
    const fixture = await createFixture('process', 'processBoundary', {
      businessCapability: 'CRM Contact Preference Management',
      startsWhen: ['An anonymized CRM contact update is accepted.'],
      endsWhen: ['The CRM contact view confirms the update.'],
      futureCrmBoundaryHint: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateProcessBoundaryList('excludes', 'Authentication credential lifecycle');
    expect(emitted).toHaveBeenLastCalledWith({
      businessCapability: 'CRM Contact Preference Management',
      startsWhen: ['An anonymized CRM contact update is accepted.'],
      endsWhen: ['The CRM contact view confirms the update.'],
      excludes: ['Authentication credential lifecycle'],
      futureCrmBoundaryHint: { reviewed: true }
    });
    expect(fixture.nativeElement.querySelector('#process-boundary-business-capability')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#process-boundary-endsWhen')).toBeTruthy();
  });

  it('normalizes a legacy CRM process boundary only after guided editing', async () => {
    const fixture = await createFixture('process', 'processBoundary', ['CRM contact confirmation is visible.']);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.processBoundaryList('endsWhen')).toEqual(['CRM contact confirmation is visible.']);
    fixture.componentInstance.updateProcessBoundaryList('includes', 'CRM contact preference validation');
    expect(emitted).toHaveBeenLastCalledWith({
      endsWhen: ['CRM contact confirmation is visible.'],
      includes: ['CRM contact preference validation']
    });
  });

  it('edits guided CRM lifecycle triggers and transitions while preserving extensions', async () => {
    const fixture = await createFixture('process', 'lifecycle', {
      triggers: [{ type: 'api', name: 'CRM contact update', futureCrmTriggerHint: true }],
      statuses: ['requested', 'applied'],
      transitions: [{ from: 'requested', to: 'applied', trigger: 'CRM validation succeeds.', futureCrmTransitionHint: true }],
      terminalStates: ['applied'],
      futureCrmLifecycleHint: { reviewed: true }
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateLifecycleTrigger(0, 'exchange', 'crm.contact.update');
    expect(emitted).toHaveBeenLastCalledWith(expect.objectContaining({
      triggers: [expect.objectContaining({
        type: 'api', name: 'CRM contact update', exchange: 'crm.contact.update', futureCrmTriggerHint: true
      })],
      futureCrmLifecycleHint: { reviewed: true }
    }));
    fixture.componentRef.setInput('value', emitted.mock.calls.at(-1)?.[0]);
    fixture.detectChanges();
    fixture.componentInstance.updateLifecycleTransition(0, 'trigger', 'CRM validation and persistence succeed.');
    expect(emitted).toHaveBeenLastCalledWith(expect.objectContaining({
      transitions: [expect.objectContaining({
        from: 'requested', to: 'applied', trigger: 'CRM validation and persistence succeed.', futureCrmTransitionHint: true
      })],
      futureCrmLifecycleHint: { reviewed: true }
    }));
    expect(fixture.nativeElement.querySelector('#lifecycle-trigger-name-0')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#lifecycle-transition-to-0')).toBeTruthy();
  });

  it('reads a legacy CRM lifecycle list as statuses and writes the canonical guided object', async () => {
    const fixture = await createFixture('process', 'lifecycle', ['requested', 'applied']);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    expect(fixture.componentInstance.lifecycleList('statuses')).toEqual(['requested', 'applied']);
    fixture.componentInstance.updateLifecycleList('terminalStates', 'applied');
    expect(emitted).toHaveBeenLastCalledWith({ statuses: ['requested', 'applied'], terminalStates: ['applied'] });
  });

  it('edits guided CRM completion evidence without losing future categories', async () => {
    const fixture = await createFixture('process', 'completionSignals', {
      successful: ['CRM contact confirmation is recorded.'],
      partial: ['CRM projection is pending.'],
      futureCrmCompletionHint: ['Preserve anonymized CRM extension.']
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateCompletionSignalList('failed', 'CRM validation rejection is recorded.');
    expect(emitted).toHaveBeenLastCalledWith({
      successful: ['CRM contact confirmation is recorded.'],
      partial: ['CRM projection is pending.'],
      failed: ['CRM validation rejection is recorded.'],
      futureCrmCompletionHint: ['Preserve anonymized CRM extension.']
    });
    expect(fixture.nativeElement.querySelector('#completion-signals-successful')).toBeTruthy();
  });

  it.each([
    ['system', 'participants', { externalOwner: 'CRM platform provider' }],
    ['system', 'runtime', { configurationDirectory: 'crm/contact-service' }],
    ['repository', 'evidence', [{ sourceRef: 'crm/contact-service/pom.xml', evidenceType: 'build-definition' }]],
    ['repository', 'llmToolHints', { answerWhenUserMentions: ['CRM contact validation'] }],
    ['bounded-context', 'localLanguageSummary', ['CRM contact means a communication profile.']],
    ['bounded-context', 'scope', { includes: ['CRM contact preference validation'] }],
    ['bounded-context', 'semanticBoundary', { ownsLanguage: ['CRM contact preference'] }],
    ['bounded-context', 'evidence', [{ sourceRef: 'Anonymized CRM glossary', evidenceType: 'domain-documentation' }]],
    ['bounded-context', 'llmToolHints', { answerWhenUserMentions: ['CRM contact preference'] }],
    ['integration', 'participants', { source: { system: 'crm-contact-core' }, targets: [{ system: 'crm-profile-store' }] }],
    ['repository', 'git', { provider: 'gitlab', projectPath: 'crm/contact-service' }],
    ['process', 'participants', { actors: ['CRM agent'], primarySystems: ['crm-contact-core'] }],
    ['process', 'steps', [{ id: 'accept-update', name: 'Accept CRM update' }]],
    ['process', 'processBoundary', { endsWhen: ['CRM update confirmed'] }],
    ['process', 'lifecycle', { statuses: ['requested', 'applied'], transitions: [{ from: 'requested', to: 'applied', trigger: 'CRM validation succeeds.' }] }],
    ['process', 'completionSignals', { successful: ['CRM confirmation recorded'] }],
    ['system', 'matchSignals', { strong: { routes: ['/crm/contacts'] } }],
    ['system', 'relations', [{ type: 'uses', targetType: 'process', target: 'crm-contact-update' }]],
    ['process', 'failureModes', [{ id: 'crm-failure', name: 'CRM failure', summary: 'Anonymized CRM failure.' }]],
    ['process', 'dataAndArtifacts', { inputArtifacts: ['Anonymized CRM request'] }],
    ['repository', 'sourceCoverage', { status: 'partial', scannedSources: ['CRM module'] }],
    ['bounded-context', 'gaps', [{ id: 'crm-gap', summary: 'Confirm CRM boundary.' }]]
  ] as Array<[OperationalContextWritableType, string, unknown]>)('makes every nested CRM %s.%s label keyboard discoverable', async (type, path, value) => {
    const fixture = await createFixture(type, path, value);
    const labels = Array.from(fixture.nativeElement.querySelectorAll('.structured-label')) as HTMLElement[];
    expect(labels.length).toBeGreaterThan(0);
    expect(labels.every((label) => label.getAttribute('tabindex') === '0')).toBe(true);
    expect(labels.every((label) => label.querySelector('mat-icon')?.textContent?.includes('help'))).toBe(true);
  });

  it('edits the CRM Git identity while preserving server-owned and future fields in component state', async () => {
    const fixture = await createFixture('repository', 'git', {
      provider: 'gitlab',
      projectPath: 'crm/contact-service',
      inferred: true,
      futureCrmGitHint: 'preserve'
    });
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.updateObject('defaultBranch', 'main');

    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({
      projectPath: 'crm/contact-service',
      defaultBranch: 'main',
      inferred: true,
      futureCrmGitHint: 'preserve'
    }));
    expect(fixture.nativeElement.querySelector('#git-project-path')).toBeTruthy();
  });

  it('assigns existing CRM systems to process roles without duplicate choices', async () => {
    const fixture = await createFixture('process', 'participants', {
      actors: ['CRM agent'],
      primarySystems: ['crm-contact-core'],
      futureCrmParticipantHint: 'preserve'
    });
    fixture.componentRef.setInput('referenceOptions', {
      system: [
        { id: 'crm-contact-core', label: 'CRM Contact Core' },
        { id: 'crm-profile-store', label: 'CRM Profile Store' }
      ]
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.availableProcessSystems('supportingSystems').map((option) => option.id))
      .toEqual(['crm-profile-store']);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);
    fixture.componentInstance.updateObject('supportingSystems', ['crm-profile-store']);
    expect(emitted).toHaveBeenCalledWith(expect.objectContaining({
      primarySystems: ['crm-contact-core'],
      supportingSystems: ['crm-profile-store'],
      futureCrmParticipantHint: 'preserve'
    }));
  });

  it('builds and reorders guided CRM process steps while preserving extensions', async () => {
    const fixture = await createFixture('process', 'steps', [
      { id: 'accept-update', name: 'Accept CRM update', futureCrmStepHint: 'preserve' },
      { id: 'publish-update', name: 'Publish CRM update' }
    ]);
    const emitted = vi.fn();
    fixture.componentInstance.valueChange.subscribe(emitted);

    fixture.componentInstance.updateProcessStepStrongTerms(0, 'accept CRM update');
    expect(emitted).toHaveBeenLastCalledWith(expect.arrayContaining([
      expect.objectContaining({
        id: 'accept-update',
        futureCrmStepHint: 'preserve',
        matchSignals: { strong: { terms: ['accept CRM update'] } }
      })
    ]));

    fixture.componentRef.setInput('value', [
      { id: 'accept-update', name: 'Accept CRM update' },
      { id: 'publish-update', name: 'Publish CRM update' }
    ]);
    fixture.detectChanges();
    fixture.componentInstance.moveProcessStep(1, -1);
    expect(emitted).toHaveBeenLastCalledWith([
      expect.objectContaining({ id: 'publish-update' }),
      expect.objectContaining({ id: 'accept-update' })
    ]);
    expect(fixture.nativeElement.querySelector('textarea[id^="step-summary-"]')).toBeTruthy();
  });

  async function createFixture(
    type: OperationalContextWritableType,
    path: string,
    value: unknown,
    entityId = ''
  ) {
    await TestBed.configureTestingModule({ imports: [ContextStructuredFieldEditorComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ContextStructuredFieldEditorComponent);
    fixture.componentRef.setInput('field', adapter.fields(type).find((field) => field.path === path)!);
    fixture.componentRef.setInput('entityType', type);
    fixture.componentRef.setInput('entityId', entityId);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture;
  }
});
