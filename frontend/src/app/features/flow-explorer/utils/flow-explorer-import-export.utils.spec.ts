import { FlowExplorerJobStateSnapshot } from '../models/flow-explorer.models';
import {
  buildFlowExplorerExportEnvelope,
  buildFlowExplorerExportFileName,
  parseImportedFlowExplorerAnalysis
} from './flow-explorer-import-export.utils';

describe('flow-explorer-import-export utils', () => {
  it('should build and parse a completed Flow Explorer export envelope', () => {
    const exportedAt = '2026-06-18T10:00:00Z';
    const envelope = buildFlowExplorerExportEnvelope(flowExplorerJob(), exportedAt);

    const imported = parseImportedFlowExplorerAnalysis(envelope);

    expect(envelope.schema).toBe('incident-tracker.flow-explorer-export');
    expect(envelope.version).toBe(1);
    expect(imported.exportedAt).toBe(exportedAt);
    expect(imported.job.jobId).toBe('flow-job-1');
    expect(imported.job.result?.aiResponse?.flowSteps[0]?.title).toBe('Load customer');
  });

  it('should reject non-terminal Flow Explorer exports', () => {
    const envelope = buildFlowExplorerExportEnvelope(
      flowExplorerJob({ status: 'ANALYZING', result: null }),
      '2026-06-18T10:00:00Z'
    );

    expect(() => parseImportedFlowExplorerAnalysis(envelope)).toThrow(
      'Import wspiera tylko zakończone Flow Explorer analizy.'
    );
  });

  it('should build a stable download file name', () => {
    expect(buildFlowExplorerExportFileName(flowExplorerJob(), '2026-06-18T10:00:00')).toBe(
      'flow-explorer-crm-service-api-customers-id-completed-20260618-100000.json'
    );
  });
});

function flowExplorerJob(
  overrides: Partial<FlowExplorerJobStateSnapshot> = {}
): FlowExplorerJobStateSnapshot {
  return {
    jobId: 'flow-job-1',
    systemId: 'crm-service',
    endpointId: 'crm-api:GET /api/customers/{id}',
    httpMethod: 'GET',
    endpointPath: '/api/customers/{id}',
    branch: 'main',
    documentationPreset: 'ANALYST_OVERVIEW',
    focusAreas: ['BUSINESS_FLOW'],
    aiModel: 'gpt-5-mini',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: 'COMPLETED',
    currentStepLabel: 'AI result ready',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-06-18T10:00:00Z',
    updatedAt: '2026-06-18T10:00:01Z',
    completedAt: '2026-06-18T10:00:02Z',
    steps: [],
    contextSnapshot: null,
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    chatMessages: [],
    preparedPrompt: 'canonical prompt',
    result: {
      status: 'COMPLETED',
      systemId: 'crm-service',
      endpointId: 'crm-api:GET /api/customers/{id}',
      httpMethod: 'GET',
      endpointPath: '/api/customers/{id}',
      branch: 'main',
      userIntentSummary: 'Explain customer lookup.',
      audienceSummary: 'Tester friendly.',
      confidence: 'high',
      visibilityLimits: [],
      prompt: 'canonical prompt',
      usage: null,
      aiResponse: {
        userIntentSummary: 'Explain customer lookup.',
        audienceSummary: 'Tester friendly.',
        endpointContract: null,
        flowSteps: [
          {
            order: 1,
            title: 'Load customer',
            plainLanguage: 'Load a customer aggregate.',
            technicalGrounding: 'CustomerService.getCustomer',
            sourceRefs: ['CustomerService.getCustomer L30-L44']
          }
        ],
        businessRules: [],
        validations: [],
        persistence: [],
        externalIntegrations: [],
        testScenarios: [],
        risksAndEdgeCases: [],
        openQuestions: [],
        visibilityLimits: [],
        sourceReferences: [],
        confidence: 'high'
      }
    },
    ...overrides
  };
}
