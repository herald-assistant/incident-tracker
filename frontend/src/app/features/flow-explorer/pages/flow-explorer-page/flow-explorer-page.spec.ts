import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AnalysisApiService } from '../../../../core/services/analysis-api.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import {
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerJobStateSnapshot,
  FlowExplorerSystemOption
} from '../../models/flow-explorer.models';
import { FlowExplorerApiService } from '../../services/flow-explorer-api.service';
import { buildFlowExplorerExportEnvelope } from '../../utils/flow-explorer-import-export.utils';
import { FlowExplorerPageComponent } from './flow-explorer-page';

describe('FlowExplorerPageComponent', () => {
  let flowExplorerApi: FlowExplorerApiServiceMock;
  let analysisApi: AnalysisApiServiceMock;
  let historyApi: AnalysisRunHistoryApiServiceMock;

  beforeEach(async () => {
    analysisApi = {
      getAiModelOptions: vi.fn(() => of(aiModelOptions()))
    };
    historyApi = {
      getRun: vi.fn(() => of(localFlowExplorerRunDetail())),
      sendChatMessage: vi.fn(() =>
        of(localFlowExplorerRunDetail({ chatMessages: completedChatMessages() }))
      ),
      applyResultUpdate: vi.fn(() => of(localFlowExplorerRunDetail())),
      rejectResultUpdate: vi.fn(() => of(localFlowExplorerRunDetail()))
    };
    flowExplorerApi = {
      getConfig: vi.fn(() => of({ defaultBranch: 'main' })),
      getSystems: vi.fn(() => of([systemOption('crm-service'), systemOption('catalog-core')])),
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
      applyResultUpdate: vi.fn(() =>
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
      rejectResultUpdate: vi.fn(() =>
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
      providers: [
        { provide: FlowExplorerApiService, useValue: flowExplorerApi },
        { provide: AnalysisApiService, useValue: analysisApi },
        { provide: AnalysisRunHistoryApiService, useValue: historyApi },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap({}))
          }
        }
      ]
    }).compileComponents();
  });

  it('should load configured default branch and system catalog on init', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const branchInput = compiled.querySelector('input[type="text"]') as HTMLInputElement;

    expect(flowExplorerApi.getConfig).toHaveBeenCalledTimes(1);
    expect(flowExplorerApi.getSystems).toHaveBeenCalledTimes(1);
    expect(analysisApi.getAiModelOptions).toHaveBeenCalledTimes(1);
    expect(branchInput.value).toBe('main');
    expect(compiled.textContent).toContain('Endpoint documentation workspace');
    expect(compiled.textContent).toContain('Select application');
    expect(compiled.textContent).toContain('2 applications');
    expect(compiled.textContent).toContain('Default backend (gpt-5.4)');
    expect(compiled.textContent).not.toContain('Customer relationship core API.');
  });

  it('should show mini loaders in async select indicators while options are loading', async () => {
    const systems = new Subject<FlowExplorerSystemOption[]>();
    const endpointInventoryResponse = new Subject<FlowExplorerEndpointInventoryResponse>();
    const modelOptions = new Subject<AnalysisAiModelOptionsResponse>();
    vi.mocked(flowExplorerApi.getSystems).mockReturnValue(systems.asObservable());
    vi.mocked(flowExplorerApi.getEndpointInventory).mockReturnValue(endpointInventoryResponse.asObservable());
    vi.mocked(analysisApi.getAiModelOptions).mockReturnValue(modelOptions.asObservable());
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();

    let controls = selectControls(fixture.nativeElement);
    expect(controls[0]?.querySelector('.flow-explorer-select-loader')).not.toBeNull();
    expect(controls[0]?.getAttribute('aria-busy')).toBe('true');
    expect(controls[4]?.querySelector('.flow-explorer-select-loader')).not.toBeNull();
    expect(controls[5]?.querySelector('.flow-explorer-select-loader')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.flow-explorer-select-loader')).toHaveLength(3);

    systems.next([systemOption('crm-service'), systemOption('catalog-core')]);
    systems.complete();
    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');

    controls = selectControls(fixture.nativeElement);
    expect(controls[0]?.querySelector('.flow-explorer-select-loader')).toBeNull();
    expect(controls[1]?.querySelector('.flow-explorer-select-loader')).not.toBeNull();
    expect(controls[1]?.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelectorAll('.flow-explorer-select-loader')).toHaveLength(3);

    endpointInventoryResponse.next(endpointInventory());
    endpointInventoryResponse.complete();
    modelOptions.next(aiModelOptions());
    modelOptions.complete();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.flow-explorer-select-loader')).toHaveLength(0);
  });

  it('should filter systems locally', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    openApplicationSelect(fixture.nativeElement);
    fixture.detectChanges();
    setInputValue(
      fixture.nativeElement,
      '.flow-explorer-select__search input[type="search"]',
      'catalog'
    );
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Catalog Core');
    expect(compiled.textContent).not.toContain('CRM Service');
  });

  it('should load endpoint inventory for selected system and show endpoint details', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(flowExplorerApi.getEndpointInventory).toHaveBeenCalledWith('crm-service', {
      branch: 'main'
    });
    expect(compiled.textContent).toContain('feature/FLOW-42');
    expect(compiled.textContent).toContain('1 endpoint');
    openEndpointSelect(compiled);
    fixture.detectChanges();
    expect(compiled.textContent).toContain('GET');
    expect(compiled.textContent).toContain('/api/customers/{id}');
    expect(compiled.textContent).toContain('CustomerController.getCustomer');
    expect(compiled.textContent).toContain('L12-L24');
  });

  it('should keep selected endpoint preview focused on method, path and description', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    const preview = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.flow-explorer-target-preview'
    );

    expect(preview?.textContent).toContain('GET');
    expect(preview?.textContent).toContain('/api/customers/{id}');
    expect(preview?.textContent).toContain('Customer lookup');
    expect(preview?.textContent).not.toContain('CustomerController');
    expect(preview?.textContent).not.toContain('getCustomer');
    expect(preview?.textContent).not.toContain('L12-L24');
  });

  it('should clear loaded endpoints when branch changes and reload with the new branch', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    setInputValue(fixture.nativeElement, 'input[type="text"]', 'release-candidate');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Load endpoints for branch/ref');

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
    selectSystem(fixture, 'CRM Service');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('CRM Service');
    expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('GitLab unavailable.');
  });

  it('should start a job from selected endpoint, scope controls and user instructions', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');
    selectGoal(fixture, 'Test scenarios');
    selectSectionMode(fixture, 'Validations', 'Deep');
    selectAiModel(fixture, 'GPT-5.4 mini');
    selectReasoningEffort(fixture, 'High');
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
      goal: 'TEST_SCENARIOS',
      focusAreas: ['FUNCTIONAL_FLOW', 'VALIDATIONS'],
      sectionModes: [
        { id: 'FUNCTIONAL_FLOW', mode: 'DEEP' },
        { id: 'VALIDATIONS', mode: 'DEEP' },
        { id: 'PERSISTENCE', mode: 'COMPACT' },
        { id: 'INTEGRATIONS', mode: 'COMPACT' }
      ],
      userInstructions: 'Skup sie na negatywnych scenariuszach walidacji statusu klienta.',
      model: 'gpt-5.4-mini',
      reasoningEffort: 'high'
    });
    expect(flowExplorerApi.getJob).toHaveBeenCalledWith('flow-job-1');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('COMPLETED');
    expect(compiled.textContent).toContain('AI result ready');
    expect(compiled.textContent).toContain('Prompt po przygotowaniu deterministic context');
  });

  it('should start a risk detection job from the selected endpoint', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');
    selectGoal(fixture, 'Risk detection');
    selectSectionMode(fixture, 'Integrations', 'Deep');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    expect(flowExplorerApi.startJob).toHaveBeenCalledWith({
      systemId: 'crm-service',
      endpointId: 'crm-api:GET /api/customers/{id}',
      httpMethod: 'GET',
      endpointPath: '/api/customers/{id}',
      branch: 'main',
      goal: 'RISK_DETECTION',
      focusAreas: ['FUNCTIONAL_FLOW', 'INTEGRATIONS'],
      sectionModes: [
        { id: 'FUNCTIONAL_FLOW', mode: 'DEEP' },
        { id: 'VALIDATIONS', mode: 'COMPACT' },
        { id: 'PERSISTENCE', mode: 'COMPACT' },
        { id: 'INTEGRATIONS', mode: 'DEEP' }
      ],
      userInstructions: undefined,
      model: undefined,
      reasoningEffort: undefined
    });
  });

  it('should render structured AI result for a completed job', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const markdownBlocks = compiled.querySelectorAll('app-markdown-content.flow-explorer-markdown');
    const referenceLists = compiled.querySelectorAll<HTMLDetailsElement>('details.flow-explorer-reference-list');
    const appendix = compiled.querySelector<HTMLDetailsElement>('details.flow-explorer-result-appendix');
    expect(compiled.textContent).toContain('AI result');
    expect(compiled.textContent).toContain('Overview');
    expect(compiled.textContent).toContain('Deep Discovery');
    expect(compiled.textContent).toContain('The endpoint reads the requested customer');
    expect(markdownBlocks.length).toBe(5);
    expect(markdownBlocks[0]?.querySelector('strong')?.textContent).toBe('The endpoint');
    expect(compiled.querySelector('.flow-explorer-result-summary')).toBeNull();
    expect(compiled.querySelector('.flow-explorer-result__heading .field-hint')).toBeNull();
    expect(referenceLists.length).toBe(4);
    referenceLists.forEach((referenceList) => expect(referenceList.open).toBe(false));
    expect(referenceLists[0]?.querySelector('summary')?.textContent).toContain('References');
    expect(appendix?.open).toBe(false);
    expect(appendix?.querySelector('summary')?.textContent).toContain(
      'Limits, questions and references'
    );
    expect(appendix?.querySelector('summary')?.textContent).toContain('1 limits');
    expect(appendix?.querySelector('summary')?.textContent).toContain('1 questions');
    expect(appendix?.querySelector('summary')?.textContent).toContain('1 references');
    expect(compiled.textContent).toContain('Co sprawdzić dalej');
    expect(compiled.textContent).toContain('1 sugestia');
    expect(compiled.textContent).toContain('Sprawdz, czy nieaktywny klient powinien blokowac ten flow.');
    expect(compiled.textContent).toContain('Functional flow');
    expect(compiled.textContent).toContain('Customer id is required before the lookup can continue.');
    expect(compiled.textContent).toContain('CustomerRepository.findById');
    expect(compiled.textContent).toContain('Tokens');
    expect(compiled.textContent).toContain('2,820');
    expect(compiled.textContent).toContain('Credits');
    expect(compiled.textContent).toContain('Dollars');
    expect(compiled.textContent).not.toContain('Cost $0.0123');

    const usage = compiled.querySelector('.flow-explorer-result-usage') as HTMLElement | null;
    expect(usage?.getAttribute('aria-label')).toContain('Wywolania modelu: 1');
    expect(usage?.getAttribute('aria-label')).toContain('Dollars: $0.00');
  });

  it('should copy the completed Flow Explorer result without action controls', async () => {
    const clipboard = mockRichClipboard();
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    try {
      fixture.detectChanges();
      selectSystem(fixture, 'CRM Service');
      selectEndpoint(fixture, '/api/customers/{id}');

      clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
      fixture.detectChanges();

      clickButtonContaining(fixture.nativeElement, 'Copy result');
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
      fixture.detectChanges();

      const clipboardItems = clipboard.write.mock.calls[0]?.[0] as TestClipboardItem[] | undefined;
      const copiedText = await readBlobText(clipboardItems?.[0]?.items['text/plain']);

      expect(clipboard.write).toHaveBeenCalledTimes(1);
      expect(copiedText).toContain('The endpoint reads the requested customer');
      expect(copiedText).toContain('Functional flow');
      expect(copiedText).not.toContain('Copy result');
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Copied');
    } finally {
      clipboard.restore();
    }
  });

  it('should copy a recommended follow-up prompt', async () => {
    const clipboard = mockRichClipboard();
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    try {
      fixture.detectChanges();
      selectSystem(fixture, 'CRM Service');
      selectEndpoint(fixture, '/api/customers/{id}');

      clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const copyButton = compiled.querySelector<HTMLButtonElement>(
        '.flow-explorer-follow-up-prompt__copy'
      );
      copyButton?.click();
      await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
      fixture.detectChanges();

      expect(clipboard.writeText).toHaveBeenCalledWith(
        'Sprawdz, czy nieaktywny klient powinien blokowac ten flow.'
      );
      expect(compiled.textContent).toContain('Co sprawdzić dalej');
    } finally {
      clipboard.restore();
    }
  });

  it('should export completed Flow Explorer results to a JSON file', async () => {
    const downloadMock = mockFileDownload();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();
    clickButtonContaining(fixture.nativeElement, 'Export');

    const blob = downloadMock.createObjectURL.mock.calls[0]?.[0] as Blob;
    expect(downloadMock.createObjectURL).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(blob.type).toBe('application/json');
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    expect(downloadMock.revokeObjectURL).toHaveBeenCalledWith('blob:flow-explorer-export');

    clickSpy.mockRestore();
    downloadMock.restore();
  });

  it('should import a completed Flow Explorer export as a read-only result', async () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);
    const exportedJob = jobSnapshot({
      status: 'COMPLETED',
      currentStepCode: 'COMPLETED',
      currentStepLabel: 'AI result ready',
      preparedPrompt: 'imported canonical prompt',
      result: flowExplorerResult()
    });
    const fileContent = JSON.stringify(
      buildFlowExplorerExportEnvelope(exportedJob, '2026-06-18T10:00:00Z')
    );
    const file = new File(
      [fileContent],
      'flow-explorer-export.json',
      { type: 'application/json' }
    );
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: () => Promise.resolve(fileContent)
    });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file]
    });

    fixture.detectChanges();
    await fixture.componentInstance.importFlowExplorerAnalysis({ target: input } as unknown as Event);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const chatInput = compiled.querySelector(
      '.chat-input'
    ) as HTMLTextAreaElement;
    const topLevelSections = Array.from(
      compiled.querySelectorAll<HTMLDetailsElement>('details.flow-explorer-collapsible')
    );
    const sectionByEyebrow = new Map(
      topLevelSections.map((section) => [
        section.querySelector('.section-eyebrow')?.textContent?.trim(),
        section
      ])
    );
    expect(compiled.textContent).toContain('Wczytany plik: flow-explorer-export.json');
    expect(compiled.textContent).toContain('AI result');
    expect(compiled.textContent).toContain('Importowany zapis jest tylko do odczytu');
    expect(sectionByEyebrow.get('Flow Explorer')?.open).toBe(false);
    expect(sectionByEyebrow.get('Job state')?.open).toBe(false);
    expect(sectionByEyebrow.get('AI result')?.open).toBe(true);
    expect(compiled.querySelector<HTMLDetailsElement>('details.analysis-chat')?.open).toBe(false);
    expect(compiled.querySelector<HTMLDetailsElement>('details.panel-card--progress')?.open).toBe(false);
    expect(chatInput.disabled).toBe(true);
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
            aiResponse: null
          }
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Deep Discovery');
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
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Przebieg pracy Copilota');
    expect(compiled.textContent).toContain('Tok działania AI');
    expect(compiled.textContent).toContain('Tools');
    expect(compiled.textContent).toContain('CustomerController.getCustomer');
    expect(compiled.textContent).toContain('Endpoint handler.');
    expect(compiled.textContent).toContain('gitlab_read_repository_file');
    expect(compiled.textContent).toContain('Payload zdarzenia');
    expect(compiled.textContent).toContain('Tool response contained the focused handler method.');
  });

  it('should render shared workflow progress when no Copilot trace was captured', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Przebieg analizy');
    expect(compiled.textContent).toContain('Deterministic endpoint context');
    expect(compiled.textContent).toContain('Context coverage');
  });

  it('should send follow-up chat messages for a completed Flow Explorer job', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');

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

  it('should open a Before/After review modal for Flow Explorer follow-up result updates', () => {
    const resultUpdate = updatedFlowExplorerAiResponse();
    vi.mocked(flowExplorerApi.getJob).mockReturnValue(
      of(
        jobSnapshot({
          status: 'COMPLETED',
          currentStepCode: 'COMPLETED',
          currentStepLabel: 'AI result ready',
          preparedPrompt: 'canonical prompt',
          result: flowExplorerResult(),
          chatMessages: completedChatMessages(resultUpdate)
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');
    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();

    const reviewButton = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.chat-message__review-button'
    );
    reviewButton?.click();
    fixture.detectChanges();

    const modal = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.flow-explorer-review-dialog'
    );
    expect(modal?.textContent).toContain('Review result changes');
    expect(modal?.textContent).toContain('The endpoint reads the requested customer');
    expect(modal?.textContent).not.toContain('Updated overview after review.');
    buttonContaining(modal!, 'After')?.click();
    fixture.detectChanges();

    expect(reviewButton?.textContent).toContain('Review changes');
    expect(modal?.textContent).toContain('Updated overview after review.');
    expect(fixture.componentInstance.pendingResultUpdateReview()).toEqual({
      messageId: 'chat-2',
      resultUpdate
    });
  });

  it('should apply a reviewed Flow Explorer result update for a live job', () => {
    const resultUpdate = updatedFlowExplorerAiResponse();
    vi.mocked(flowExplorerApi.getJob).mockReturnValue(
      of(
        jobSnapshot({
          status: 'COMPLETED',
          currentStepCode: 'COMPLETED',
          currentStepLabel: 'AI result ready',
          preparedPrompt: 'canonical prompt',
          result: flowExplorerResult(),
          chatMessages: completedChatMessages(resultUpdate)
        })
      )
    );
    vi.mocked(flowExplorerApi.applyResultUpdate).mockReturnValue(
      of(
        jobSnapshot({
          status: 'COMPLETED',
          currentStepCode: 'COMPLETED',
          currentStepLabel: 'AI result ready',
          preparedPrompt: 'canonical prompt',
          result: {
            ...flowExplorerResult(),
            aiResponse: resultUpdate
          },
          chatMessages: completedChatMessages()
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    selectSystem(fixture, 'CRM Service');
    selectEndpoint(fixture, '/api/customers/{id}');
    clickButtonContaining(fixture.nativeElement, 'Run Flow Explorer');
    fixture.detectChanges();
    clickButtonContaining(fixture.nativeElement, 'Review changes');
    fixture.detectChanges();
    const modal = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.flow-explorer-review-dialog'
    );
    buttonContaining(modal!, 'Apply')?.click();
    fixture.detectChanges();

    expect(flowExplorerApi.applyResultUpdate).toHaveBeenCalledWith('flow-job-1', 'chat-2', {
      aiResponse: resultUpdate
    });
    expect((fixture.nativeElement as HTMLElement).querySelector('.flow-explorer-review-dialog')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Updated overview after review.');
  });

  it('should keep imported result update reviews read-only', async () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);
    const resultUpdate = updatedFlowExplorerAiResponse();
    const exportedJob = jobSnapshot({
      status: 'COMPLETED',
      currentStepCode: 'COMPLETED',
      currentStepLabel: 'AI result ready',
      preparedPrompt: 'imported canonical prompt',
      result: flowExplorerResult(),
      chatMessages: completedChatMessages(resultUpdate)
    });
    const fileContent = JSON.stringify(
      buildFlowExplorerExportEnvelope(exportedJob, '2026-06-18T10:00:00Z')
    );
    const file = new File([fileContent], 'flow-explorer-export.json', { type: 'application/json' });
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: () => Promise.resolve(fileContent)
    });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file]
    });

    fixture.detectChanges();
    await fixture.componentInstance.importFlowExplorerAnalysis({ target: input } as unknown as Event);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Review changes');
    clickButtonContaining(fixture.nativeElement, 'Review changes');
    fixture.detectChanges();

    const modal = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.flow-explorer-review-dialog'
    );
    expect(modal?.textContent).toContain('Imported result is read-only');
    expect(modal?.textContent).toContain('The endpoint reads the requested customer');
    expect(modal?.textContent).not.toContain('Updated overview after review.');
    buttonContaining(modal!, 'After')?.click();
    fixture.detectChanges();
    expect(modal?.textContent).toContain('Updated overview after review.');
    expect(buttonContaining(modal!, 'Apply')).toBeUndefined();
    expect(buttonContaining(modal!, 'Reject')).toBeUndefined();
  });

  it('should apply reviewed result updates for local Flow Explorer runs through history API', () => {
    const resultUpdate = updatedFlowExplorerAiResponse();
    vi.mocked(historyApi.getRun).mockReturnValue(
      of(localFlowExplorerRunDetail({ chatMessages: completedChatMessages(resultUpdate) }))
    );
    vi.mocked(historyApi.applyResultUpdate).mockReturnValue(
      of(
        localFlowExplorerRunDetail({
          result: {
            ...flowExplorerResult(),
            aiResponse: resultUpdate
          },
          chatMessages: completedChatMessages()
        })
      )
    );
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    (fixture.componentInstance as unknown as {
      loadLocalFlowExplorerRun(analysisId: string): void;
    }).loadLocalFlowExplorerRun('flow-job-1');
    fixture.detectChanges();
    clickButtonContaining(fixture.nativeElement, 'Review changes');
    fixture.detectChanges();
    const modal = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      '.flow-explorer-review-dialog'
    );
    buttonContaining(modal!, 'Apply')?.click();
    fixture.detectChanges();

    expect(historyApi.applyResultUpdate).toHaveBeenCalledWith('flow-job-1', 'chat-2', {
      aiResponse: resultUpdate
    });
    expect(flowExplorerApi.applyResultUpdate).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Updated overview after review.');
    const sourceEnvelope = fixture.componentInstance.exportState()?.sourceEnvelope as
      | { payload?: { job?: FlowExplorerJobStateSnapshot } }
      | undefined;
    expect(sourceEnvelope?.payload?.job?.result?.aiResponse?.overview.markdown).toBe(
      'Updated overview after review.'
    );
    expect(sourceEnvelope?.payload?.job?.chatMessages.map((message) => message.resultUpdate ?? null)).toEqual([
      null,
      null
    ]);
  });

  it('should continue a local Flow Explorer run through analysis history API', () => {
    const fixture = TestBed.createComponent(FlowExplorerPageComponent);

    fixture.detectChanges();
    (fixture.componentInstance as unknown as {
      loadLocalFlowExplorerRun(analysisId: string): void;
    }).loadLocalFlowExplorerRun('flow-job-1');
    fixture.detectChanges();
    setChatTextareaValue(fixture.nativeElement, 'Gdzie jest walidacja?');
    fixture.detectChanges();

    clickButtonContaining(fixture.nativeElement, 'Send');
    fixture.detectChanges();

    expect(historyApi.sendChatMessage).toHaveBeenCalledWith('flow-job-1', {
      message: 'Gdzie jest walidacja?'
    });
    expect(flowExplorerApi.sendChatMessage).not.toHaveBeenCalled();
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
  | 'getConfig'
  | 'getSystems'
  | 'getEndpointInventory'
  | 'startJob'
  | 'sendChatMessage'
  | 'applyResultUpdate'
  | 'rejectResultUpdate'
  | 'getJob'
>;
type AnalysisApiServiceMock = Pick<AnalysisApiService, 'getAiModelOptions'>;
type AnalysisRunHistoryApiServiceMock = Pick<
  AnalysisRunHistoryApiService,
  'getRun' | 'sendChatMessage' | 'applyResultUpdate' | 'rejectResultUpdate'
>;

function setInputValue(nativeElement: HTMLElement, selector: string, value: string): void {
  const input = nativeElement.querySelector(selector) as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function selectControls(nativeElement: HTMLElement): HTMLButtonElement[] {
  return Array.from(nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control'));
}

function selectSystem(fixture: ComponentFixture<FlowExplorerPageComponent>, label: string): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openApplicationSelect(nativeElement);
  fixture.detectChanges();
  const systemButton = Array.from(
    nativeElement.querySelectorAll<HTMLButtonElement>('button.flow-explorer-select-option')
  ).find((button) => button.textContent?.includes(label));
  systemButton?.click();
  fixture.detectChanges();
}

function openApplicationSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[0]?.click();
}

function openEndpointSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[1]?.click();
}

function openGoalSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[2]?.click();
}

function openFocusAreasSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[3]?.click();
}

function openAiModelSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[4]?.click();
}

function openReasoningEffortSelect(nativeElement: HTMLElement): void {
  nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select__control')[5]?.click();
}

function clickLoadEndpoints(nativeElement: HTMLElement): void {
  nativeElement.querySelector<HTMLButtonElement>('.flow-explorer-control-actions button')?.click();
}

function selectEndpoint(fixture: ComponentFixture<FlowExplorerPageComponent>, path: string): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openEndpointSelect(nativeElement);
  fixture.detectChanges();
  const endpointButton = Array.from(
    nativeElement.querySelectorAll<HTMLButtonElement>('.flow-explorer-select-option__select')
  ).find((button) => button.textContent?.includes(path));
  endpointButton?.click();
  fixture.detectChanges();
}

function selectSectionMode(
  fixture: ComponentFixture<FlowExplorerPageComponent>,
  sectionLabel: string,
  modeLabel: string
): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openFocusAreasSelect(nativeElement);
  fixture.detectChanges();
  const row = Array.from(
    nativeElement.querySelectorAll<HTMLElement>('.flow-explorer-section-mode-row')
  ).find((candidate) => candidate.textContent?.includes(sectionLabel));
  const button = Array.from(row?.querySelectorAll<HTMLButtonElement>('button') ?? []).find(
    (candidate) => candidate.textContent?.trim() === modeLabel
  );
  button?.click();
  fixture.detectChanges();
}

function selectGoal(fixture: ComponentFixture<FlowExplorerPageComponent>, label: string): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openGoalSelect(nativeElement);
  fixture.detectChanges();
  buttonContaining(nativeElement, label)?.click();
  fixture.detectChanges();
}

function selectAiModel(fixture: ComponentFixture<FlowExplorerPageComponent>, label: string): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openAiModelSelect(nativeElement);
  fixture.detectChanges();
  buttonContaining(nativeElement, label)?.click();
  fixture.detectChanges();
}

function selectReasoningEffort(fixture: ComponentFixture<FlowExplorerPageComponent>, label: string): void {
  const nativeElement = fixture.nativeElement as HTMLElement;
  openReasoningEffortSelect(nativeElement);
  fixture.detectChanges();
  buttonContaining(nativeElement, label)?.click();
  fixture.detectChanges();
}

function clickButtonContaining(nativeElement: HTMLElement, label: string): void {
  buttonContaining(nativeElement, label)?.click();
}

function buttonContaining(nativeElement: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(nativeElement.querySelectorAll<HTMLButtonElement>('button')).find((candidate) =>
    candidate.textContent?.includes(label)
  );
}

function mockFileDownload(): {
  createObjectURL: ReturnType<typeof vi.fn>;
  revokeObjectURL: ReturnType<typeof vi.fn>;
  restore: () => void;
} {
  const originalCreateObjectURL = URL.createObjectURL;
  const originalRevokeObjectURL = URL.revokeObjectURL;
  const createObjectURL = vi.fn(() => 'blob:flow-explorer-export');
  const revokeObjectURL = vi.fn();

  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: createObjectURL
  });
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: revokeObjectURL
  });

  return {
    createObjectURL,
    revokeObjectURL,
    restore: () => {
      Object.defineProperty(URL, 'createObjectURL', {
        configurable: true,
        value: originalCreateObjectURL
      });
      Object.defineProperty(URL, 'revokeObjectURL', {
        configurable: true,
        value: originalRevokeObjectURL
      });
    }
  };
}

interface TestClipboardItem {
  readonly items: Record<string, Blob>;
}

function mockRichClipboard(): {
  write: ReturnType<typeof vi.fn>;
  writeText: ReturnType<typeof vi.fn>;
  restore: () => void;
} {
  const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  const originalClipboardItem = Object.getOwnPropertyDescriptor(globalThis, 'ClipboardItem');
  const write = vi.fn(() => Promise.resolve());
  const writeText = vi.fn(() => Promise.resolve());
  class FlowExplorerTestClipboardItem implements TestClipboardItem {
    constructor(readonly items: Record<string, Blob>) {}
  }

  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { write, writeText }
  });
  Object.defineProperty(globalThis, 'ClipboardItem', {
    configurable: true,
    value: FlowExplorerTestClipboardItem
  });

  return {
    write,
    writeText,
    restore: () => {
      restoreProperty(navigator, 'clipboard', originalClipboard);
      restoreProperty(globalThis, 'ClipboardItem', originalClipboardItem);
    }
  };
}

function restoreProperty(
  target: object,
  propertyName: string,
  descriptor: PropertyDescriptor | undefined
): void {
  if (descriptor) {
    Object.defineProperty(target, propertyName, descriptor);
    return;
  }
  delete (target as Record<string, unknown>)[propertyName];
}

function readBlobText(blob: Blob | undefined): Promise<string> {
  if (!blob) {
    return Promise.resolve('');
  }
  const blobWithText = blob as Blob & { text?: () => Promise<string> };
  if (typeof blobWithText.text === 'function') {
    return blobWithText.text();
  }

  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

function setTextareaValue(nativeElement: HTMLElement, value: string): void {
  const textarea = nativeElement.querySelector('textarea') as HTMLTextAreaElement;
  textarea.value = value;
  textarea.dispatchEvent(new Event('input'));
}

function setChatTextareaValue(nativeElement: HTMLElement, value: string): void {
  const textarea = nativeElement.querySelector(
    '.chat-input'
  ) as HTMLTextAreaElement;
  textarea.value = value;
  textarea.dispatchEvent(new Event('input'));
}

function systemOption(systemId: string): FlowExplorerSystemOption {
  const crm = systemId === 'crm-service';
  return {
    systemId,
    name: crm ? 'CRM Service' : 'Catalog Core',
    shortName: crm ? 'CRM' : 'Catalog',
    kind: 'internal-application',
    lifecycleStatus: 'active',
    operationalStatus: 'healthy',
    criticality: crm ? 'high' : 'medium',
    summary: crm ? 'Customer relationship core API.' : 'Catalog operations API.',
    aliases: crm ? ['crm'] : ['catalog'],
    repositoryCount: crm ? 2 : 1,
    codeSearchScopeCount: 1,
    ownerTeamIds: crm ? ['team-crm'] : ['team-catalog']
  };
}

function aiModelOptions(): AnalysisAiModelOptionsResponse {
  return {
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: [
      {
        id: 'gpt-5.4-mini',
        name: 'GPT-5.4 mini',
        supportsReasoningEffort: true,
        reasoningEfforts: ['low', 'medium', 'high'],
        defaultReasoningEffort: 'medium'
      },
      {
        id: 'crm-fast-model',
        name: 'CRM fast model',
        supportsReasoningEffort: false,
        reasoningEfforts: [],
        defaultReasoningEffort: ''
      }
    ]
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
  const snapshot: FlowExplorerJobStateSnapshot = {
    jobId: 'flow-job-1',
    systemId: 'crm-service',
    endpointId: 'crm-api:GET /api/customers/{id}',
    httpMethod: 'GET',
    endpointPath: '/api/customers/{id}',
    branch: 'main',
    goal: 'DEEP_DISCOVERY',
    focusAreas: ['FUNCTIONAL_FLOW'],
    sectionModes: defaultSectionModes(),
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
  return {
    ...snapshot,
    steps: overrides.steps ?? defaultWorkflowSteps(snapshot),
    contextSections: overrides.contextSections ?? defaultContextSections()
  };
}

function localFlowExplorerRunDetail(
  jobOverrides: Partial<FlowExplorerJobStateSnapshot> = {}
): LocalAnalysisRunDetailResponse {
  const job = jobSnapshot({
    status: 'COMPLETED',
    currentStepCode: 'COMPLETED',
    currentStepLabel: 'AI result ready',
    preparedPrompt: 'local canonical prompt',
    result: flowExplorerResult(),
    ...jobOverrides
  });

  return {
    analysisId: 'flow-job-1',
    feature: 'flow-explorer',
    name: 'GET /api/customers/{id} Deep Discovery',
    createdAt: '2026-06-18T10:00:00Z',
    updatedAt: '2026-06-18T10:03:00Z',
    completedAt: '2026-06-18T10:03:00Z',
    exportEnvelope: buildFlowExplorerExportEnvelope(job, '2026-06-18T10:03:00Z'),
    continuationEnabled: true
  };
}

function defaultSectionModes(): FlowExplorerJobStateSnapshot['sectionModes'] {
  return [
    { id: 'FUNCTIONAL_FLOW', title: 'Functional flow', mode: 'DEEP' },
    { id: 'VALIDATIONS', title: 'Validations', mode: 'COMPACT' },
    { id: 'PERSISTENCE', title: 'Persistence', mode: 'COMPACT' },
    { id: 'INTEGRATIONS', title: 'Integrations', mode: 'COMPACT' }
  ];
}

function defaultWorkflowSteps(
  snapshot: FlowExplorerJobStateSnapshot
): FlowExplorerJobStateSnapshot['steps'] {
  const contextStatus =
    snapshot.status === 'QUEUED'
      ? 'PENDING'
      : snapshot.status === 'COLLECTING_CONTEXT'
        ? 'IN_PROGRESS'
        : 'COMPLETED';
  const aiStatus =
    snapshot.status === 'COMPLETED'
      ? 'COMPLETED'
      : snapshot.status === 'ANALYZING'
        ? 'IN_PROGRESS'
        : 'PENDING';

  return [
    {
      code: 'DETERMINISTIC_CONTEXT',
      label: 'Deterministic endpoint context',
      phase: 'CONTEXT',
      status: contextStatus,
      message:
        contextStatus === 'IN_PROGRESS'
          ? 'Backend buduje deterministic endpoint context.'
          : 'Backend zbudowal deterministic endpoint context.',
      itemCount: contextStatus === 'COMPLETED' ? 2 : null,
      startedAt: '2026-06-18T10:00:00Z',
      completedAt: contextStatus === 'COMPLETED' ? '2026-06-18T10:00:01Z' : '',
      consumesEvidence: [],
      producesEvidence: [{ provider: 'flow-explorer', category: 'endpoint-context' }],
      usage: null
    },
    {
      code: 'AI_ANALYSIS',
      label: 'AI endpoint documentation',
      phase: 'AI',
      status: aiStatus,
      message:
        aiStatus === 'COMPLETED'
          ? 'AI przygotowalo dokumentacje endpointu.'
          : 'AI buduje dokumentacje endpointu.',
      itemCount: null,
      startedAt: aiStatus === 'PENDING' ? '' : '2026-06-18T10:00:01Z',
      completedAt: aiStatus === 'COMPLETED' ? '2026-06-18T10:00:03Z' : '',
      consumesEvidence: [{ provider: 'flow-explorer', category: 'endpoint-context' }],
      producesEvidence: [],
      usage: snapshot.result?.usage ?? null
    }
  ];
}

function defaultContextSections(): FlowExplorerJobStateSnapshot['contextSections'] {
  return [
    {
      provider: 'flow-explorer',
      category: 'endpoint-context',
      items: [
        {
          title: 'Endpoint target',
          attributes: [
            { name: 'systemId', value: 'crm-service' },
            { name: 'endpointPath', value: '/api/customers/{id}' }
          ]
        },
        {
          title: 'Context coverage',
          attributes: [
            { name: 'flowNodeCount', value: '1' },
            { name: 'snippetCardCount', value: '1' }
          ]
        }
      ]
    }
  ];
}

function completedChatMessages(resultUpdate?: unknown): FlowExplorerJobStateSnapshot['chatMessages'] {
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
      prompt: 'follow-up prompt',
      ...(resultUpdate === undefined ? {} : { resultUpdate })
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
        markdown: '**The endpoint** reads the requested customer and returns its current CRM profile.',
        confidence: 'high',
        sourceRefs: ['CustomerController.getCustomer L12-L24']
      },
      sections: [
        {
          id: 'FUNCTIONAL_FLOW',
          title: 'Functional flow',
          mode: 'deep',
          markdown: 'The controller delegates customer lookup to the CRM service and returns the profile when it is available.',
          sourceRefs: ['CustomerController.getCustomer L12-L24'],
          visibilityLimits: [],
          openQuestions: []
        },
        {
          id: 'VALIDATIONS',
          title: 'Validations',
          mode: 'deep',
          markdown: 'Customer id is required before the lookup can continue.',
          sourceRefs: ['CustomerService.getCustomer L30-L44'],
          visibilityLimits: [],
          openQuestions: []
        },
        {
          id: 'PERSISTENCE',
          title: 'Persistence',
          mode: 'compact',
          markdown: 'CustomerRepository.findById loads the aggregate.',
          sourceRefs: ['CustomerRepository.findById L10-L18'],
          visibilityLimits: [],
          openQuestions: []
        },
        {
          id: 'INTEGRATIONS',
          title: 'Integrations',
          mode: 'compact',
          markdown: 'No external system call is visible in the initial flow.',
          sourceRefs: [],
          visibilityLimits: ['No runtime database records were queried.'],
          openQuestions: ['Confirm expected status code for inactive customers.']
        }
      ],
      globalVisibilityLimits: ['No runtime database records were queried.'],
      globalOpenQuestions: ['Confirm expected status code for inactive customers.'],
      sourceReferences: ['CustomerService.getCustomer L30-L44'],
      confidence: 'high',
      followUpPrompts: ['Sprawdz, czy nieaktywny klient powinien blokowac ten flow.']
    }
  };
}

function updatedFlowExplorerAiResponse(): NonNullable<
  NonNullable<FlowExplorerJobStateSnapshot['result']>['aiResponse']
> {
  const current = flowExplorerResult().aiResponse!;
  return {
    ...current,
    overview: {
      ...current.overview,
      markdown: 'Updated overview after review.',
      confidence: 'medium'
    },
    sections: current.sections.map((section) =>
      section.id === 'PERSISTENCE'
        ? {
            ...section,
            markdown: 'Updated persistence details after review.',
            sourceRefs: ['CustomerRepository.findById L50-L72']
          }
        : section
    ),
    confidence: 'medium',
    sourceReferences: ['CustomerRepository.findById L50-L72']
  };
}
