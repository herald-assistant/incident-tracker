import { FlowExplorerJobStateSnapshot } from '../models/flow-explorer.models';
import {
  buildFlowExplorerExportDiagnostics,
  buildFlowExplorerExportEnvelope,
  buildFlowExplorerExportFileName,
  FLOW_EXPLORER_RESULT_CONTRACT,
  parseImportedFlowExplorerAnalysis
} from './flow-explorer-import-export.utils';

describe('flow-explorer-import-export utils', () => {
  it('should build and parse a completed Flow Explorer export envelope', () => {
    const exportedAt = '2026-06-18T10:00:00Z';
    const envelope = buildFlowExplorerExportEnvelope(flowExplorerJob(), exportedAt);

    const imported = parseImportedFlowExplorerAnalysis(envelope);

    expect(envelope.schema).toBe('tdw.flow-explorer-export');
    expect(envelope.version).toBe(2);
    expect(envelope.payload.type).toBe('flow-explorer-analysis');
    expect(envelope.payload.resultContract).toBe(FLOW_EXPLORER_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.resultContract).toBe(FLOW_EXPLORER_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.request.goal).toBe('DEEP_DISCOVERY');
    expect(envelope.payload.diagnostics.request.sectionModes[0]?.mode).toBe('DEEP');
    expect(envelope.payload.diagnostics.result.sectionModes.FUNCTIONAL_FLOW).toBe('deep');
    expect(envelope.payload.diagnostics.result.followUpPromptCount).toBe(1);
    expect(envelope.payload.diagnostics.context.snippetCardCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.contextEvidenceItemCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.toolEvidenceItemCount).toBe(1);
    expect(envelope.payload.diagnostics.artifacts.map((artifact) => artifact.name)).toContain(
      'flow-explorer-result.md'
    );
    const reportArtifact = envelope.payload.diagnostics.artifacts.find(
      (artifact) => artifact.name === 'analysisReport'
    );
    expect(reportArtifact?.included).toBe(true);
    expect(reportArtifact?.itemCount).toBe(2);
    expect(envelope.payload.diagnostics.resultMarkdown).toContain('## Functional flow');
    expect(envelope.payload.diagnostics.resultMarkdown).toContain('### Recommended follow-up prompts');
    expect(envelope.payload.diagnostics.resultMarkdown).not.toContain('class CustomerService');
    expect(imported.exportedAt).toBe(exportedAt);
    expect(imported.job.jobId).toBe('flow-job-1');
    expect(imported.job.goal).toBe('DEEP_DISCOVERY');
    expect(imported.job.result?.aiResponse?.sections[0]?.title).toBe('Functional flow');
    expect(imported.job.result?.aiResponse?.sections[0]?.markdown).toContain('Flow krok po kroku');
    expect(imported.job.report?.reportId).toBe('flow-report-1');
    expect(imported.job.report?.sections[0]?.title).toBe('Functional flow');
    expect(imported.job.result?.aiResponse?.followUpPrompts).toEqual([
      'Sprawdz, czy nieaktywny klient powinien blokowac ten flow.'
    ]);
  });

  it('should build diagnostics for the current goal-based result contract', () => {
    const diagnostics = buildFlowExplorerExportDiagnostics(flowExplorerJob());

    expect(diagnostics.target.endpointPath).toBe('/api/customers/{id}');
    expect(diagnostics.context.clippingNotes).toContain('No clipping reported by deterministic context.');
    expect(diagnostics.workflow.usageIncluded).toBe(true);
    expect(diagnostics.artifacts.find((artifact) => artifact.name === 'preparedPrompt')?.included).toBe(true);
    expect(diagnostics.artifacts.find((artifact) => artifact.name === 'analysisReport')?.included).toBe(true);
    expect(diagnostics.resultMarkdown).toContain('CustomerRepository.findById loads the aggregate.');
  });

  it('should reject non-completed Flow Explorer exports before building a file', () => {
    expect(() =>
      buildFlowExplorerExportEnvelope(
        flowExplorerJob({ status: 'ANALYZING', result: null }),
        '2026-06-18T10:00:00Z'
      )
    ).toThrow('Import i eksport wspiera tylko zakończone Flow Explorer analizy COMPLETED.');
  });

  it('should reject imported files without the v2 diagnostics contract', () => {
    const envelope = {
      schema: 'tdw.flow-explorer-export',
      version: 1,
      exportedAt: '2026-06-18T10:00:00Z',
      payload: {
        type: 'flow-explorer-job',
        job: flowExplorerJob()
      }
    };

    expect(() => parseImportedFlowExplorerAnalysis(envelope)).toThrow(
      'Ten plik eksportu Flow Explorera ma nieobsługiwaną wersję formatu.'
    );
  });

  it('should reject unsupported ai response fields on import', () => {
    const envelope = buildFlowExplorerExportEnvelope(flowExplorerJob(), '2026-06-18T10:00:00Z');
    const payload = envelope.payload.job.result?.aiResponse as unknown as Record<string, unknown>;
    payload['extraNotes'] = [];

    expect(() => parseImportedFlowExplorerAnalysis(envelope)).toThrow(
      'Flow Explorer aiResponse zawiera nieobsługiwane pola: extraNotes.'
    );
  });

  it('should reject incomplete section sets on import', () => {
    const envelope = buildFlowExplorerExportEnvelope(flowExplorerJob(), '2026-06-18T10:00:00Z');
    envelope.payload.job.result!.aiResponse!.sections = envelope.payload.job.result!.aiResponse!.sections.filter(
      (section) => section.id !== 'INTEGRATIONS'
    );

    expect(() => parseImportedFlowExplorerAnalysis(envelope)).toThrow(
      'Flow Explorer result nie zawiera sekcji: INTEGRATIONS.'
    );
  });

  it('should import a result with an off section omitted', () => {
    const job = flowExplorerJob({
      focusAreas: ['FUNCTIONAL_FLOW'],
      sectionModes: [
        { id: 'FUNCTIONAL_FLOW', title: 'Functional flow', mode: 'DEEP' },
        { id: 'VALIDATIONS', title: 'Validations', mode: 'COMPACT' },
        { id: 'PERSISTENCE', title: 'Persistence', mode: 'COMPACT' },
        { id: 'INTEGRATIONS', title: 'Integrations', mode: 'OFF' }
      ]
    });
    job.result!.aiResponse!.sections = job.result!.aiResponse!.sections.filter(
      (section) => section.id !== 'INTEGRATIONS'
    );
    const envelope = buildFlowExplorerExportEnvelope(job, '2026-06-18T10:00:00Z');

    const imported = parseImportedFlowExplorerAnalysis(envelope);

    expect(imported.job.sectionModes.find((sectionMode) => sectionMode.id === 'INTEGRATIONS')?.mode).toBe('OFF');
    expect(imported.job.result?.aiResponse?.sections.map((section) => section.id)).toEqual([
      'FUNCTIONAL_FLOW',
      'VALIDATIONS',
      'PERSISTENCE'
    ]);
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
    focusAreas: ['FUNCTIONAL_FLOW'],
    sectionModes: [
      { id: 'FUNCTIONAL_FLOW', title: 'Functional flow', mode: 'DEEP' },
      { id: 'VALIDATIONS', title: 'Validations', mode: 'COMPACT' },
      { id: 'PERSISTENCE', title: 'Persistence', mode: 'COMPACT' },
      { id: 'INTEGRATIONS', title: 'Integrations', mode: 'COMPACT' }
    ],
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
    chatMessages: [],
    preparedPrompt: 'canonical prompt',
    contextSnapshot: {
      systemId: 'crm-service',
      systemName: 'CRM Service',
      requestedBranch: 'main',
      resolvedRef: 'feature/FLOW-42',
      endpointId: 'crm-api:GET /api/customers/{id}',
      httpMethod: 'GET',
      endpointPath: '/api/customers/{id}',
      repositories: [
        {
          repositoryId: 'crm-api',
          projectName: 'crm-api',
          projectPath: 'platform/crm/crm-api',
          resolvedRef: 'feature/FLOW-42',
          attempted: true,
          selected: true,
          limitations: []
        }
      ],
      flowNodes: [
        {
          id: 'CustomerService',
          role: 'service',
          filePath: 'src/main/java/com/example/crm/CustomerService.java',
          methods: [{ methodName: 'getCustomer', lineStart: 30, lineEnd: 44 }],
          reason: 'Main CRM lookup flow.',
          confidence: 'HIGH',
          limitations: []
        }
      ],
      relations: [{ sourceId: 'CustomerController', targetId: 'CustomerService', relation: 'delegates' }],
      snippetCards: [
        {
          id: 'crm-api:CustomerService:getCustomer',
          projectName: 'crm-api',
          filePath: 'src/main/java/com/example/crm/CustomerService.java',
          role: 'service',
          methods: [{ methodName: 'getCustomer', lineStart: 30, lineEnd: 44 }],
          requestedStartLine: 30,
          requestedEndLine: 44,
          returnedStartLine: 30,
          returnedEndLine: 44,
          totalLines: 90,
          truncated: false,
          reason: 'Main CRM lookup flow.',
          content: 'class CustomerService { CustomerProfile getCustomer(String id) { return repository.findById(id); } }',
          characterCount: 95,
          limitations: []
        }
      ],
      limitations: [],
      suggestedNextReads: [],
      coverage: {
        endpointResolved: true,
        repositoryRefCount: 1,
        attemptedRepositoryCount: 1,
        flowNodeCount: 1,
        methodCount: 1,
        relationCount: 1,
        snippetCardCount: 1,
        snippetCharacterCount: 95,
        snippetBudgetReached: false,
        unresolvedReferenceCount: 0,
        limitationCount: 0,
        maxDepthReached: false,
        maxFilesReached: false,
        readFileLimitReached: false,
        confidence: 'HIGH'
      }
    },
    contextSections: [
      {
        provider: 'flow-explorer',
        category: 'endpoint-context',
        items: [
          {
            title: 'Endpoint target',
            attributes: [{ name: 'endpointPath', value: '/api/customers/{id}' }]
          }
        ]
      }
    ],
    toolEvidenceSections: [
      {
        provider: 'gitlab',
        category: 'focused-read',
        items: [
          {
            title: 'CustomerService.getCustomer',
            attributes: [{ name: 'lines', value: 'L30-L44' }]
          }
        ]
      }
    ],
    aiActivityEvents: [
      {
        eventId: 'event-1',
        parentEventId: '',
        type: 'tool.call',
        category: 'tool',
        status: 'COMPLETED',
        title: 'Read CRM service method',
        summary: 'AI requested focused CRM service context.',
        turnId: 'turn-1',
        interactionId: 'interaction-1',
        toolCallId: 'tool-1',
        toolName: 'gitlab_read_repository_file',
        timestamp: '2026-06-18T10:00:01Z',
        details: {}
      }
    ],
    toolFeedback: [
      {
        feedbackId: 'feedback-1',
        targetToolName: 'gitlab_read_repository_file',
        targetToolCallId: 'tool-1',
        feedbackToolCallId: 'feedback-tool-1',
        usefulness: 'useful',
        expectedDataReceived: 'yes',
        issueCategory: 'none',
        improvementArea: '',
        confidence: 'high',
        summaryForOperator: 'Focused CRM service method was available.',
        suggestedImprovement: '',
        createdAt: '2026-06-18T10:00:02Z'
      }
    ],
    result: {
      status: 'COMPLETED',
      systemId: 'crm-service',
      endpointId: 'crm-api:GET /api/customers/{id}',
      httpMethod: 'GET',
      endpointPath: '/api/customers/{id}',
      branch: 'main',
      goal: 'DEEP_DISCOVERY',
      prompt: 'canonical prompt',
      usage: {
        inputTokens: 2100,
        outputTokens: 720,
        cacheReadTokens: 0,
        cacheWriteTokens: 0,
        totalTokens: 2820,
        cost: 0.0123,
        apiDurationMs: 1200,
        apiCallCount: 1,
        model: 'gpt-5-mini',
        contextTokenLimit: 128000,
        contextCurrentTokens: 5400,
        contextMessages: 8
      },
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
            id: 'FUNCTIONAL_FLOW',
            title: 'Functional flow',
            mode: 'deep',
            markdown:
              '- **Cel funkcjonalny:** pokazac profil klienta CRM.\n' +
              '- **Flow krok po kroku:** 1. system przyjmuje request; 2. waliduje id; 3. dociaga profil klienta; 4. zwraca dane bez zapisu stanu.\n' +
              '- **Koordynacja i routing:** sciezka zalezy od identyfikatora klienta i statusu profilu.\n' +
              '- **Kalkulacje i reguly funkcjonalne:** profil jest widoczny tylko dla potwierdzonego klienta CRM.\n' +
              '- **Rozgalezienia zalezne od kontekstu:** brak rekordu konczy flow kontrolowanym brakiem danych.\n' +
              '- **Handoffy i efekty uboczne:** endpoint odczytuje dane i zwraca odpowiedz; szczegoly danych sa w sekcji Persistence.\n' +
              '- **Akcent goal:** wskazac glowne warianty funkcjonalne.',
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
        confidence: 'high',
        followUpPrompts: ['Sprawdz, czy nieaktywny klient powinien blokowac ten flow.']
      }
    },
    report: {
      reportId: 'flow-report-1',
      header: 'Flow Explorer: GET /api/customers/{id}',
      subHeader: 'CRM Service / feature/FLOW-42',
      markdownSummary: 'Customer lookup pobiera profil klienta bez zapisu stanu.',
      sections: [
        {
          id: 'FUNCTIONAL_FLOW',
          title: 'Functional flow',
          order: 1,
          markdown: 'CustomerRepository.findById loads the aggregate.',
          meta: {
            references: [
              {
                type: 'code',
                label: 'CustomerService.getCustomer',
                target: 'src/main/java/com/example/crm/CustomerService.java:L30-L44',
                description: 'Main CRM lookup flow.'
              }
            ],
            visibilityLimits: [],
            openQuestions: [],
            gaps: [],
            confidence: 'high',
            warnings: []
          }
        }
      ],
      meta: {
        references: [],
        visibilityLimits: [],
        openQuestions: [],
        gaps: [],
        confidence: 'high',
        warnings: []
      }
    },
    ...overrides
  };
}
