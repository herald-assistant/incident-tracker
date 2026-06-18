import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerJobStateSnapshot,
  FlowExplorerSystemOption
} from '../../models/flow-explorer.models';
import { FlowExplorerApiService } from '../../services/flow-explorer-api.service';
import { FlowExplorerPageComponent } from './flow-explorer-page';

describe('FlowExplorerPageComponent', () => {
  let flowExplorerApi: FlowExplorerApiServiceMock;

  beforeEach(async () => {
    flowExplorerApi = {
      getConfig: vi.fn(() => of({ defaultBranch: 'main' })),
      getSystems: vi.fn(() => of([systemOption('crm-service'), systemOption('billing-core')])),
      getEndpointInventory: vi.fn(() => of(endpointInventory())),
      startJob: vi.fn(() => of(jobSnapshot({ status: 'COLLECTING_CONTEXT' }))),
      sendChatMessage: vi.fn(() =>
        of(
          jobSnapshot({
            status: 'COMPLETED',
            currentStepCode: 'COMPLETED',
            currentStepLabel: 'AI result ready',
            preparedPrompt: 'canonical prompt',
            result: flowExplorerResult(),
            chatMessages: completedChatMessages()
          })
        )
      ),
      getJob: vi.fn(() =>
        of(
          jobSnapshot({
            status: 'COMPLETED',
            currentStepCode: 'COMPLETED',
            currentStepLabel: 'AI result ready',
            preparedPrompt: 'canonical prompt\nsnippetCards: selected controller method',
            result: flowExplorerResult()
          })
        )
      )
    };

    await TestBed.configureTestingModule({
      imports: [FlowExplorerPageComponent],
      providers: [{ provide: FlowExplorerApiService, useValue: flowExplorerApi }]
    }).compileComponents();
  });

  it('should load configured default branch and system catalog on init', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const branchInput = compiled.querySelector('input[type="text"]') as HTMLInputElement;

    expect(flowExplorerApi.getConfig).toHaveBeenCalledTimes(1);
    expect(flowExplorerApi.getSystems).toHaveBeenCalledTimes(1);
    expect(branchInput.value).toBe('main');
    expect(compiled.textContent).toContain('CRM Service');
    expect(compiled.textContent).toContain('Billing Core');
    expect(compiled.textContent).toContain('2 systems');
  });

  it('should filter systems locally', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    setInputValue(fixture.nativeElement, 'input[type="search"]', 'billing');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Billing Core');
    expect(compiled.textContent).not.toContain('CRM Service');
  });

  it('should load endpoint inventory for selected system and show endpoint details', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(flowExplorerApi.getEndpointInventory).toHaveBeenCalledWith('crm-service', {
      branch: 'main'
    });
    expect(compiled.textContent).toContain('feature/FLOW-42');
    expect(compiled.textContent).toContain('1 endpoint');
    expect(compiled.textContent).toContain('GET');
    expect(compiled.textContent).toContain('/api/customers/{id}');
    expect(compiled.textContent).toContain('CustomerController.getCustomer');
    expect(compiled.textContent).toContain('L12-L24');
  });

  it('should clear loaded endpoints when branch changes and reload with the new branch', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    setInputValue(fixture.nativeElement, 'input[type="text"]', 'release-candidate');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Wczytaj endpointy');

    clickLoadEndpoints(fixture.nativeElement);
    fixture.detectChanges();

    expect(flowExplorerApi.getEndpointInventory).toHaveBeenLastCalledWith('crm-service', {
      branch: 'release-candidate'
    });
  });

  it('should render endpoint inventory errors without clearing the selected system', () => {
    vi.mocked(flowExplorerApi.getEndpointInventory).mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 503,
            error: { message: 'GitLab unavailable.' }
          })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('CRM Service');
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('GitLab unavailable.');
  });

  it('should start a job from selected endpoint, scope controls and user instructions', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    clickButtonContaining(fixture.nativeElement, 'Test preparation');
    clickButtonContaining(fixture.nativeElement, 'Validations');
    setTextareaValue(
      fixture.nativeElement,
      'Skup sie na negatywnych scenariuszach walidacji statusu klienta.'
    );
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    expect(flowExplorerApi.startJob).toHaveBeenCalledWith({
      systemId: 'crm-service',
      endpointId: 'crm-api:GET /api/customers/{id}',
      httpMethod: 'GET',
      endpointPath: '/api/customers/{id}',
      branch: 'main',
      documentationPreset: 'TEST_PREPARATION',
      focusAreas: ['BUSINESS_FLOW', 'VALIDATIONS'],
      userInstructions: 'Skup sie na negatywnych scenariuszach walidacji statusu klienta.'
    });
    expect(flowExplorerApi.getJob).toHaveBeenCalledWith('flow-job-1');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('COMPLETED');
    expect(compiled.textContent).toContain('AI result ready');
    expect(compiled.textContent).toContain('Prepared prompt preview');
  });

  it('should render structured AI result for a completed job', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('AI result');
    expect(compiled.textContent).toContain('Endpoint contract');
    expect(compiled.textContent).toContain('Finds customer details by id.');
    expect(compiled.textContent).toContain('Load customer');
    expect(compiled.textContent).toContain('Customer id is required.');
    expect(compiled.textContent).toContain('CustomerRepository.findById');
    expect(compiled.textContent).toContain('Tokens 2,820');
  });

  it('should show a controlled fallback when structured AI response is missing', () => {
    vi.mocked(flowExplorerApi.getJob).mockReturnValue(
      of(
        jobSnapshot({
          status: 'COMPLETED',
          currentStepCode: 'COMPLETED',
          currentStepLabel: 'AI result ready',
          result: {
            ...flowExplorerResult(),
            userIntentSummary: 'Fallback result',
            aiResponse: null
          }
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Fallback result');
    expect(compiled.textContent).toContain('structured response body is not available');
  });

  it('should render AI activity, tool evidence and feedback trace', () => {
    vi.mocked(flowExplorerApi.getJob).mockReturnValue(
      of(
        jobSnapshot({
          status: 'COMPLETED',
          currentStepCode: 'COMPLETED',
          currentStepLabel: 'AI result ready',
          result: flowExplorerResult(),
          toolEvidenceSections: [
            {
              provider: 'GitLab',
              category: 'code-search',
              items: [
                {
                  title: 'CustomerController.getCustomer',
                  attributes: [
                    { name: 'file', value: 'CustomerController.java' },
                    { name: 'lines', value: 'L12-L24' },
                    { name: 'reason', value: 'Endpoint handler.' }
                  ]
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
              title: 'Read endpoint context',
              summary: 'AI requested a focused GitLab read.',
              turnId: 'turn-1',
              interactionId: 'interaction-1',
              toolCallId: 'tool-call-1',
              toolName: 'gitlab_read_repository_file',
              timestamp: '2026-06-18T10:00:02Z',
              details: {}
            }
          ],
          toolFeedback: [
            {
              feedbackId: 'feedback-1',
              targetToolName: 'gitlab_read_repository_file',
              targetToolCallId: 'tool-call-1',
              feedbackToolCallId: 'feedback-tool-call-1',
              usefulness: 'useful',
              expectedDataReceived: 'yes',
              issueCategory: 'none',
              improvementArea: '',
              confidence: 'high',
              summaryForOperator: 'Tool response contained the focused handler method.',
              suggestedImprovement: '',
              createdAt: '2026-06-18T10:00:03Z'
            }
          ]
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Analysis trace');
    expect(compiled.textContent).toContain('1 events');
    expect(compiled.textContent).toContain('1 evidence items');
    expect(compiled.textContent).toContain('1 feedback');
    expect(compiled.textContent).toContain('GitLab · code-search');
    expect(compiled.textContent).toContain('CustomerController.getCustomer');
    expect(compiled.textContent).toContain('Read endpoint context');
    expect(compiled.textContent).toContain('gitlab_read_repository_file');
    expect(compiled.textContent).toContain('Tool response contained the focused handler method.');
  });

  it('should render an empty trace state when no trace data was captured', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No AI trace or tool evidence was captured for this run.');
  });

  it('should send follow-up chat messages for a completed Flow Explorer job', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    clickSystem(fixture.nativeElement, 'CRM Service');
    fixture.detectChanges();
    clickEndpoint(fixture.nativeElement, '/api/customers/{id}');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();
    setChatTextareaValue(fixture.nativeElement, 'Gdzie jest walidacja?');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Send');
    fixture.detectChanges();

    expect(flowExplorerApi.sendChatMessage).toHaveBeenCalledWith('flow-job-1', {
      message: 'Gdzie jest walidacja?'
    });
    expect(fixture.nativeElement.textContent).toContain('Follow-up chat');
    expect(fixture.nativeElement.textContent).toContain('Gdzie jest walidacja?');
    expect(fixture.nativeElement.textContent).toContain('Walidacja jest w CustomerService.validate.');
  });

  it('should keep run action disabled until endpoint is selected', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();

    const runButton = buttonContaining(fixture.nativeElement, 'Run Flow Explorer');

    expect(runButton?.disabled).toBe(true);
    expect(flowExplorerApi.startJob).not.toHaveBeenCalled();
  });
});

type FlowExplorerApiServiceMock = Pick<
  FlowExplorerApiService,
  'getConfig' | 'getSystems' | 'getEndpointInventory' | 'startJob' | 'sendChatMessage' | 'getJob'
>;

function setInputValue(nativeElement: HTMLElement, selector: string, value: string): void {
  const input = nativeElement.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function clickSystem(nativeElement: HTMLElement, label: string): void {
  const systemButton = Array.from(
    nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-system-row')
  ).find((button) => button.textContent?.includes(label));
  systemButton?.click();
}

function clickLoadEndpoints(nativeElement: HTMLElement): void {
  const button = buttonContaining(nativeElement, 'Load endpoints');
  button?.click();
}

function clickEndpoint(nativeElement: HTMLElement, path: string): void {
  const endpointButton = Array.from(
    nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-endpoint-row__select')
  ).find((button) => button.textContent?.includes(path));
  endpointButton?.click();
}

function clickButtonContaining(nativeElement: HTMLElement, label: string): void {
  buttonContaining(nativeElement, label)?.click();
}

function buttonContaining(nativeElement: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(nativeElement.querySelectorAll<HTMLButtonElement>('button')).find((candidate) =>
    candidate.textContent?.includes(label)
  );
}

function setTextareaValue(nativeElement: HTMLElement, value: string): void {
  const textarea = nativeElement.querySelector('textarea') as HTMLTextAreaElement;
  textarea.value = value;
  textarea.dispatchEvent(new Event('input'));
}

function setChatTextareaValue(nativeElement: HTMLElement, value: string): void {
  const textarea = nativeElement.querySelector(
    '.flow-explorer-chat-composer__textarea'
  ) as HTMLTextAreaElement;
  textarea.value = value;
  textarea.dispatchEvent(new Event('input'));
}

function systemOption(systemId: string): FlowExplorerSystemOption {
  const crm = systemId === 'crm-service';
  return {
    systemId,
    name: crm ? 'CRM Service' : 'Billing Core',
    shortName: crm ? 'CRM' : 'Billing',
    kind: 'internal-application',
    lifecycleStatus: 'active',
    operationalStatus: 'healthy',
    criticality: crm ? 'high' : 'medium',
    summary: crm ? 'Customer relationship core API.' : 'Billing operations API.',
    aliases: crm ? ['crm'] : ['billing'],
    repositoryCount: crm ? 2 : 1,
    codeSearchScopeCount: 1,
    ownerTeamIds: crm ? ['team-crm'] : ['team-billing']
  };
}

function endpointInventory(): FlowExplorerEndpointInventoryResponse {
  return {
    systemId: 'crm-service',
    requestedBranch: 'main',
    resolvedRef: 'feature/FLOW-42',
    gitLabGroup: 'platform/backend',
    endpointPathPrefix: '',
    httpMethod: '',
    repositoryCount: 2,
    scannedRepositoryCount: 2,
    endpointCount: 1,
    candidateFileCount: 7,
    scannedFileCount: 4,
    scannedFileLimitReached: false,
    repositories: [],
    endpoints: [
      {
        endpointId: 'crm-api:GET /api/customers/{id}',
        method: 'GET',
        methods: ['GET'],
        path: '/api/customers/{id}',
        pathExpression: '/api/customers/{id}',
        summary: 'Customer lookup',
        description: 'Returns customer details.',
        operationId: 'getCustomer',
        tags: ['customers'],
        controllerClass: 'CustomerController',
        handlerMethod: 'getCustomer',
        source: {
          repositoryId: 'crm-api',
          projectName: 'crm-api',
          projectPath: 'platform/backend/crm-api',
          filePath: 'src/main/java/com/example/CustomerController.java',
          lineStart: 12,
          lineEnd: 24
        },
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            type: 'String',
            description: 'Customer id.'
          }
        ],
        confidence: 'high',
        limitations: [],
        suggestedNextReads: [],
        tooltipDetails: {
          documentationSource: 'OPENAPI_YAML',
          summary: 'Customer lookup',
          description: 'Returns customer details.',
          operationId: 'getCustomer',
          tags: ['customers'],
          parameters: [
            {
              name: 'id',
              in: 'path',
              required: true,
              type: 'String',
              description: 'Customer id.'
            }
          ],
          requestTypes: ['CustomerRequest'],
          responseTypes: ['CustomerResponse'],
          annotations: ['GetMapping'],
          limitations: [],
          suggestedNextReads: []
        }
      }
    ],
    limitations: []
  };
}

function jobSnapshot(overrides: Partial<FlowExplorerJobStateSnapshot> = {}): FlowExplorerJobStateSnapshot {
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
    status: 'QUEUED',
    currentStepCode: 'DETERMINISTIC_CONTEXT',
    currentStepLabel: 'Collecting endpoint context',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-06-18T10:00:00Z',
    updatedAt: '2026-06-18T10:00:01Z',
    completedAt: '',
    steps: [],
    contextSnapshot: null,
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    chatMessages: [],
    preparedPrompt: '',
    result: null,
    ...overrides
  };
}

function completedChatMessages(): FlowExplorerJobStateSnapshot['chatMessages'] {
  return [
    {
      id: 'chat-1',
      role: 'USER',
      status: 'COMPLETED',
      content: 'Gdzie jest walidacja?',
      errorCode: '',
      errorMessage: '',
      createdAt: '2026-06-18T10:00:04Z',
      updatedAt: '2026-06-18T10:00:04Z',
      completedAt: '2026-06-18T10:00:04Z',
      toolEvidenceSections: [],
      aiActivityEvents: [],
      toolFeedback: [],
      prompt: ''
    },
    {
      id: 'chat-2',
      role: 'ASSISTANT',
      status: 'COMPLETED',
      content: 'Walidacja jest w CustomerService.validate.',
      errorCode: '',
      errorMessage: '',
      createdAt: '2026-06-18T10:00:05Z',
      updatedAt: '2026-06-18T10:00:06Z',
      completedAt: '2026-06-18T10:00:06Z',
      toolEvidenceSections: [
        {
          provider: 'GitLab',
          category: 'follow-up-file-chunk',
          items: [
            {
              title: 'CustomerService.validate',
              attributes: [{ name: 'lines', value: 'L30-L44' }]
            }
          ]
        }
      ],
      aiActivityEvents: [],
      toolFeedback: [],
      prompt: 'follow-up prompt'
    }
  ];
}

function flowExplorerResult(): NonNullable<FlowExplorerJobStateSnapshot['result']> {
  return {
    status: 'COMPLETED',
    systemId: 'crm-service',
    endpointId: 'crm-api:GET /api/customers/{id}',
    httpMethod: 'GET',
    endpointPath: '/api/customers/{id}',
    branch: 'main',
    userIntentSummary: 'Explain customer lookup for testers.',
    audienceSummary: 'Tester and analyst friendly endpoint description.',
    confidence: 'high',
    visibilityLimits: ['No runtime database records were queried.'],
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
      userIntentSummary: 'Explain customer lookup for testers.',
      audienceSummary: 'Tester and analyst friendly endpoint description.',
      endpointContract: {
        method: 'GET',
        path: '/api/customers/{id}',
        purpose: 'Finds customer details by id.',
        request: ['Path id identifies the customer.'],
        response: ['CustomerResponse with profile and status.'],
        parameters: ['id: required path parameter.']
      },
      flowSteps: [
        {
          order: 1,
          title: 'Load customer',
          plainLanguage: 'The endpoint reads the requested customer and returns its current profile.',
          technicalGrounding: 'CustomerController delegates to CustomerService.',
          sourceRefs: ['CustomerController.getCustomer L12-L24']
        }
      ],
      businessRules: ['Only active customer profiles are returned.'],
      validations: ['Customer id is required.'],
      persistence: ['CustomerRepository.findById loads the aggregate.'],
      externalIntegrations: ['No external system call is visible in the initial flow.'],
      testScenarios: ['Missing id should be rejected by routing or validation.'],
      risksAndEdgeCases: ['Customer not found behavior depends on service mapping.'],
      openQuestions: ['Confirm expected status code for inactive customers.'],
      visibilityLimits: ['No runtime database records were queried.'],
      sourceReferences: ['CustomerService.getCustomer L30-L44'],
      confidence: 'high'
    }
  };
}
