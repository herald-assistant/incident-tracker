import { UiExplorerJobStateSnapshot } from '../models/ui-explorer.models';
import { parseUiExplorerLocalRunEnvelope } from './ui-explorer-import-export.utils';

describe('UI Explorer import and export contract', () => {
  it('restores only the exact local CRM run contract', () => {
    const parsed = parseUiExplorerLocalRunEnvelope(localEnvelope());

    expect(parsed.storedAt).toBe('2026-08-15T10:04:00Z');
    expect(parsed.job.jobId).toBe('crm-ui-history-1');
    expect(parsed.job.request.screenId).toBe('crm-contact-create');
  });

  it.each([
    ['previous version', { version: 4 }],
    ['newer version', { version: 6 }],
    ['foreign schema', { schema: 'tdw.crm-foreign-run' }]
  ])('rejects %s without a compatibility fallback', (_label, override) => {
    expect(() => parseUiExplorerLocalRunEnvelope({ ...localEnvelope(), ...override })).toThrow();
  });

  it('rejects a corrupt current-version CRM snapshot', () => {
    const envelope = localEnvelope();
    envelope.payload.job = { jobId: 'crm-ui-history-corrupt' } as UiExplorerJobStateSnapshot;

    expect(() => parseUiExplorerLocalRunEnvelope(envelope)).toThrow(
      'Lokalny run UI Explorer zawiera uszkodzony snapshot.'
    );
  });
});

function localEnvelope() {
  return {
    schema: 'tdw.ui-explorer-local-run',
    version: 5,
    storedAt: '2026-08-15T10:04:00Z',
    payload: {
      type: 'ui-explorer-analysis',
      resultContract: 'ui-explorer-result-v5',
      job: crmHistorySnapshot()
    }
  };
}

function crmHistorySnapshot(): UiExplorerJobStateSnapshot {
  return {
    jobId: 'crm-ui-history-1',
    request: {
      systemId: 'crm-agent-portal',
      systemLabel: 'CRM Agent Portal',
      branch: 'main',
      screenId: 'crm-contact-create',
      sourceRevision: 'crm-revision-a1b2c3',
      sectionModes: [{ sectionId: 'OVERVIEW', mode: 'DEEP' }],
      scenarioDescription: 'Describe a synthetic CRM contact flow.',
      aiModel: 'crm-doc-model',
      reasoningEffort: 'medium'
    },
    status: 'COMPLETED',
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-08-15T10:00:00Z',
    updatedAt: '2026-08-15T10:04:00Z',
    completedAt: '2026-08-15T10:04:00Z',
    steps: [],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    preparedPrompt: null,
    result: {
      screen: {
        systemId: 'crm-agent-portal',
        screenId: 'crm-contact-create',
        label: 'CrmContactCreateComponent',
        routePattern: '/contacts/new',
        navigationContext: 'Kontakty CRM > Nowy kontakt'
      },
      scenarioDescription: 'Describe a synthetic CRM contact flow.',
      sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
      functionalOverview: 'Synthetic CRM contact documentation.',
      sections: [],
      overallConfidence: 'CONFIRMED',
      visibilityLimits: [],
      unresolvedQuestions: [],
      usage: null
    },
    report: {
      reportId: 'crm-ui-report-1',
      header: '/contacts/new',
      subHeader: 'CrmContactCreateComponent',
      markdownSummary: 'Synthetic CRM contact documentation.',
      sections: [],
      meta: {
        references: [],
        visibilityLimits: [],
        openQuestions: [],
        gaps: [],
        confidence: 'CONFIRMED',
        warnings: []
      }
    },
    usage: null,
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    outputAvailability: {
      status: 'AVAILABLE',
      code: 'READY',
      message: 'Synthetic CRM report is ready.',
      missingCapabilities: []
    },
    exportAvailable: true
  };
}
