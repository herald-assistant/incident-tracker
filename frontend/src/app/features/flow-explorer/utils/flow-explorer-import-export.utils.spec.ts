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
    expect(imported.job.goal).toBe('DEEP_DISCOVERY');
    expect(imported.job.result?.aiResponse?.sections[0]?.title).toBe('Business flow/rules');
    expect(imported.job.result?.aiResponse?.sections[0]?.markdown).toContain('Load customer aggregate');
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
    goal: 'DEEP_DISCOVERY',
    focusAreas: ['BUSINESS_FLOW_RULES'],
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
      goal: 'DEEP_DISCOVERY',
      prompt: 'canonical prompt',
      usage: null,
      aiResponse: {
        goal: 'DEEP_DISCOVERY',
        audience: 'business_or_system_analyst_tester',
        overview: {
          markdown: 'Explain customer lookup.',
          confidence: 'high',
          sourceRefs: ['CustomerController.getCustomer L12-L24']
        },
        sections: [
          {
            id: 'BUSINESS_FLOW_RULES',
            title: 'Business flow/rules',
            mode: 'deep',
            markdown: 'Load customer aggregate and return the CRM profile.',
            sourceRefs: ['CustomerService.getCustomer L30-L44'],
            visibilityLimits: [],
            openQuestions: []
          },
          {
            id: 'VALIDATIONS',
            title: 'Validations',
            mode: 'compact',
            markdown: 'Customer id is required.',
            sourceRefs: [],
            visibilityLimits: [],
            openQuestions: []
          },
          {
            id: 'PERSISTENCE',
            title: 'Persistence',
            mode: 'compact',
            markdown: 'CustomerRepository.findById loads the aggregate.',
            sourceRefs: [],
            visibilityLimits: [],
            openQuestions: []
          },
          {
            id: 'INTEGRATIONS',
            title: 'Integrations',
            mode: 'compact',
            markdown: 'No external integration is visible in initial evidence.',
            sourceRefs: [],
            visibilityLimits: [],
            openQuestions: []
          }
        ],
        globalVisibilityLimits: [],
        globalOpenQuestions: [],
        sourceReferences: [],
        confidence: 'high'
      }
    },
    ...overrides
  };
}
