import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import {
  AnalysisAiActivityEvent,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../core/models/analysis.models';
import { AnalysisStepsPanelComponent } from './analysis-steps-panel';

describe('AnalysisStepsPanelComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisStepsPanelComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();
  });

  it('should render dynatrace runtime evidence in a dedicated copilot input view', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildDynatraceStep()]);
    fixture.componentRef.setInput('evidenceSections', [buildDynatraceSection()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Co Dynatrace dołożył do analizy AI');
    expect(compiled.textContent).toContain('Grupy usług 1');
    expect(compiled.textContent).toContain('Typy metryk 2');
    expect(compiled.textContent).toContain('3 wpisów usługowych i 3 technicznych serii metryk');
    expect(compiled.textContent).toContain('Najważniejsze z Dynatrace');
    expect(compiled.textContent).toContain('Dynatrace zgrupował 3 technicznych dopasowań');
    expect(compiled.textContent).toContain('Z czym Dynatrace skojarzył incydent');
    expect(compiled.textContent).toContain('Czy Dynatrace widział problem');
    expect(compiled.textContent).toContain('P-230415');
    expect(compiled.textContent).toContain('Gateway timeout on backend');
    expect(compiled.textContent).toContain('Najważniejsze sygnały do korelacji');
    expect(compiled.textContent).toContain('łączność z bazą danych');
    expect(compiled.textContent).toContain('dostępność procesu lub usługi');
    expect(compiled.textContent).toContain('kolejki lub messaging');
    expect(compiled.textContent).toContain('Sygnały ważne dla AI');
    expect(compiled.textContent).toContain('Failed database connects [DATABASE_CONNECTION_FAILURE] (root cause)');
    expect(compiled.textContent).toContain('RabbitMQ Queue Listener lag [QUEUE_BACKLOG]');
    expect(compiled.textContent).toContain('Jak zachowywała się usługa');
    expect(compiled.textContent).toContain('Czas odpowiedzi');
    expect(compiled.textContent).toContain('To p95 z wielu endpointów');
    expect(compiled.textContent).toMatch(/12\s*s/);
    expect(compiled.textContent).toContain('Liczba błędów');
    expect(compiled.textContent).toContain('CustomerController');
  });

  it('should explain when Dynatrace visibility is unavailable instead of implying healthy runtime', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildDynatraceStep()]);
    fixture.componentRef.setInput('evidenceSections', [buildUnavailableDynatraceSection()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Status pobrania z Dynatrace: UNAVAILABLE.');
    expect(compiled.textContent).toContain('Dynatrace API request failed with HTTP 502');
    expect(compiled.textContent).toContain(
      'Missing Dynatrace metrics, problems, or component signals must be treated as lack of data'
    );
    expect(compiled.textContent).not.toContain(
      'W oknie incydentu Dynatrace nie zgłosił osobnego problemu.'
    );
  });

  it('should keep the done icon on a viewed completed step and show a loader on the running step', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', buildProgressSteps());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    let headers = Array.from(compiled.querySelectorAll('.mat-step-header')) as HTMLElement[];

    expect(headers).toHaveLength(3);
    expect(headers[1]?.querySelector('.analysis-stepper__spinner')).not.toBeNull();

    headers[0]?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    headers = Array.from(compiled.querySelectorAll('.mat-step-header')) as HTMLElement[];

    expect(
      (fixture.componentInstance as unknown as { selectedIndex(): number }).selectedIndex()
    ).toBe(0);
    expect(headers[0]?.querySelector('.analysis-stepper__icon--done')).not.toBeNull();
    expect(headers[1]?.querySelector('.analysis-stepper__spinner')).not.toBeNull();
  });

  it('should render tool quality feedback panel', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', buildCompletedSteps());
    fixture.componentRef.setInput('toolFeedback', [
      {
        feedbackId: 'feedback-1',
        targetToolName: 'gitlab_find_flow_context',
        targetToolCallId: 'tool-call-1',
        feedbackToolCallId: 'feedback-call-1',
        usefulness: 'partial',
        expectedDataReceived: 'partial',
        issueCategory: 'incomplete',
        improvementArea: 'tool_description',
        confidence: 'high',
        summaryForOperator: 'Wynik był częściowy i wymaga doprecyzowania.',
        suggestedImprovement: 'Dopisać w opisie toola zakres zwracanego flow.',
        createdAt: '2026-05-02T10:05:00Z'
      }
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Feedback jakości tooli');
    expect(compiled.textContent).toContain('gitlab_find_flow_context (tool-call-1)');
    expect(compiled.textContent).toContain('Wynik był częściowy i wymaga doprecyzowania.');
    expect(compiled.textContent).toContain('opis toola');
  });

  it('should allow switching between completed steps after the analysis finishes', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', buildCompletedSteps());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    let headers = Array.from(compiled.querySelectorAll('.mat-step-header')) as HTMLElement[];

    expect(
      (fixture.componentInstance as unknown as { selectedIndex(): number }).selectedIndex()
    ).toBe(2);

    headers[0]?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    headers = Array.from(compiled.querySelectorAll('.mat-step-header')) as HTMLElement[];

    expect(
      (fixture.componentInstance as unknown as { selectedIndex(): number }).selectedIndex()
    ).toBe(0);
    expect(headers[0]?.querySelector('.analysis-stepper__icon--done')).not.toBeNull();
    expect(headers[2]?.querySelector('.analysis-stepper__icon--done')).not.toBeNull();
  });

  it('should expose the prepared AI prompt in a copyable textarea for the operational context step', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildOperationalContextStep()]);
    fixture.componentRef.setInput('preparedPrompt', buildAiPrompt());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const promptTextarea = compiled.querySelector(
      '.prepared-prompt__textarea'
    ) as HTMLTextAreaElement | null;

    expect(compiled.textContent).toContain('Prompt po dopasowaniu kontekstu operacyjnego');
    expect(compiled.textContent).toContain(
      'To jest finalny prompt złożony z evidence i lokalnego kontekstu operacyjnego jeszcze przed wywołaniem AI.'
    );
    expect(promptTextarea).not.toBeNull();
    expect(promptTextarea?.value).toContain('correlationId: timeout-123');
    expect(promptTextarea?.value).toContain('Provider: dynatrace, category: runtime-signals');
  });

  it('should hide the prepared prompt on the final AI step and keep the result preview visible', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('preparedPrompt', buildAiPrompt());
    fixture.componentRef.setInput('result', buildAnalysisResult());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const promptTextarea = compiled.querySelector('.prepared-prompt__textarea');

    expect(compiled.textContent).toContain('Najbardziej prawdopodobny problem');
    expect(compiled.textContent).toContain('Gateway timeout on backend');
    expect(compiled.textContent).not.toContain('Prompt przygotowany do wysłania do Copilota');
    expect(promptTextarea).toBeNull();
  });

  it('should show token usage under the final AI step status', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStepWithUsage()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const usagePill = compiled.querySelector('.usage-pill') as HTMLElement | null;
    const tooltip = usagePill?.getAttribute('aria-label') ?? '';

    expect(usagePill).not.toBeNull();
    expect(usagePill?.textContent).toContain('Tokens');
    expect(usagePill?.textContent).toContain('2 820');
    expect(usagePill?.textContent).toContain('Credits');
    expect(usagePill?.textContent).toContain('1,16');
    expect(usagePill?.textContent).toContain('Dollars');
    expect(usagePill?.textContent).toContain('$0.01');
    expect(tooltip).toContain('Szacowany koszt analizy AI');
    expect(tooltip).toContain('Nowy kontekst wysłany do AI: 2 100');
    expect(tooltip).toContain('Kontekst odczytany z cache: 300');
    expect(tooltip).toContain('Odpowiedź AI: 420');
    expect(tooltip).toContain('Model zgłoszony przez SDK: gpt-5.4');
  });

  it('should render Copilot activity and tool evidence in one timeline', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('aiActivityEvents', buildAiActivityEvents());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const progressPanel = compiled.querySelector(
      'details.panel-card--progress'
    ) as HTMLDetailsElement | null;
    const copilotPanel = compiled.querySelector(
      'details.ai-workflow-panel'
    ) as HTMLDetailsElement | null;
    const timelineEntries = Array.from(compiled.querySelectorAll('.ai-work-item'));
    const runtimePayload =
      compiled.querySelector('.ai-work-item--runtime .ai-work-item__technical pre')
        ?.textContent ?? '';
    const pendingTool = compiled.querySelector('.ai-work-item__status--pending');
    const visibleTexts = Array.from(
      compiled.querySelectorAll(
        '.ai-work-item__text, .ai-work-item__markdown-preview .markdown-content'
      )
    ).map((element) => element.textContent?.trim() ?? '');

    expect(progressPanel).not.toBeNull();
    expect(copilotPanel).not.toBeNull();
    expect(progressPanel?.open).toBe(true);
    expect(copilotPanel?.open).toBe(true);
    expect(compiled.textContent).toContain('Przebieg pracy Copilota');
    expect(compiled.textContent).toContain('Tok działania AI');
    expect(compiled.textContent).toContain('Najpierw analizuję stack trace i brakujący kontekst kodu.');
    expect(compiled.textContent).toContain('Sprawdzam repozytorium przed uruchomieniem toola.');
    expect(compiled.textContent).toContain('Kontekst sesji');
    expect(compiled.textContent).toContain('Sprawdzam liczbę rekordów dla correlationId.');
    expect(compiled.textContent).toContain('cache 1');
    expect(visibleTexts.indexOf('Copilot poprosił o 1 wywołanie narzędzia.')).toBeLessThan(
      visibleTexts.indexOf('Sprawdzam liczbę rekordów dla correlationId.')
    );
    expect(visibleTexts).not.toContain('Sprawdzam repozytorium przed uruchomieniem toola.');
    expect(timelineEntries.length).toBeGreaterThanOrEqual(3);
    expect(pendingTool).not.toBeNull();
    expect(compiled.querySelector('.tool-evidence-timeline')).toBeNull();
    expect(runtimePayload).toContain('"currentTokens": 9200');
    expect(runtimePayload).toContain('"messagesLength": 6');
  });

  it('should show skill name in Copilot skill tool header', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('aiActivityEvents', [
      {
        eventId: 'event-skill-start',
        parentEventId: '',
        type: 'tool.execution_start',
        category: 'TOOL',
        status: 'STARTED',
        title: 'Tool start: skill',
        summary: 'Copilot uruchamia skill.',
        turnId: '',
        interactionId: '',
        toolCallId: 'tool-call-skill-1',
        toolName: 'skill',
        timestamp: '2026-04-14T12:00:14Z',
        details: {
          toolCallId: 'tool-call-skill-1',
          toolName: 'skill',
          arguments: {
            skill: 'flow-explorer-gitlab-tools'
          }
        }
      },
      {
        eventId: 'event-skill-complete',
        parentEventId: '',
        type: 'tool.execution_complete',
        category: 'TOOL',
        status: 'COMPLETED',
        title: 'Tool koniec',
        summary: 'Tool zakończył wykonanie poprawnie.',
        turnId: '',
        interactionId: 'interaction-skill-1',
        toolCallId: 'tool-call-skill-1',
        toolName: '',
        timestamp: '2026-04-14T12:00:15Z',
        details: {
          toolCallId: 'tool-call-skill-1',
          success: true,
          resultContentPreview:
            'Skill "flow-explorer-gitlab-tools" loaded successfully. Follow the instructions in the skill context.',
          toolTelemetry: {
            restrictedProperties: {
              skillName: 'flow-explorer-gitlab-tools'
            }
          }
        }
      }
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const skillItem = compiled.querySelector('.ai-work-item--tool') as HTMLElement | null;

    expect(skillItem).not.toBeNull();
    expect(skillItem?.querySelector('.ai-work-item__text')?.textContent).toContain(
      'Copilot wywołał skill flow-explorer-gitlab-tools.'
    );
    expect(skillItem?.querySelector('.ai-work-item__details-title')?.textContent).toContain(
      'skill flow-explorer-gitlab-tools'
    );
  });

  it('should show Java class name for GitLab method slice tool details', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('aiActivityEvents', [
      {
        eventId: 'event-method-slice-start',
        parentEventId: '',
        type: 'tool.execution_start',
        category: 'TOOL',
        status: 'STARTED',
        title: 'Tool start: gitlab_read_java_method_slice',
        summary: 'Copilot uruchamia GitLab tool.',
        turnId: '',
        interactionId: '',
        toolCallId: 'tool-call-method-slice-1',
        toolName: 'gitlab_read_java_method_slice',
        timestamp: '2026-04-14T12:00:14Z',
        details: {
          toolCallId: 'tool-call-method-slice-1',
          toolName: 'gitlab_read_java_method_slice',
          arguments: {
            reason: 'Potrzebuje szczegolow glownej metody dla walidacji.',
            filePath: 'src/main/java/com/example/crm/customer/LaunchCollateralsRequest.java'
          }
        }
      },
      {
        eventId: 'event-method-slice-complete',
        parentEventId: '',
        type: 'tool.execution_complete',
        category: 'TOOL',
        status: 'COMPLETED',
        title: 'Tool koniec',
        summary: 'Tool zakończył wykonanie poprawnie.',
        turnId: '',
        interactionId: 'interaction-method-slice-1',
        toolCallId: 'tool-call-method-slice-1',
        toolName: '',
        timestamp: '2026-04-14T12:00:15Z',
        details: {
          toolCallId: 'tool-call-method-slice-1',
          success: true
        }
      }
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const toolItem = compiled.querySelector('.ai-work-item--tool') as HTMLElement | null;
    const detailsTitle = toolItem?.querySelector('.ai-work-item__details-title')?.textContent ?? '';

    expect(toolItem).not.toBeNull();
    expect(toolItem?.querySelector('.ai-work-item__text')?.textContent).toContain(
      'Potrzebuje szczegolow glownej metody dla walidacji.'
    );
    expect(detailsTitle).toContain('LaunchCollateralsRequest.java');
    expect(detailsTitle).not.toContain('gitlab_read_java_method_slice');
  });

  it('should collapse progress and Copilot workflow when a final result is available', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('aiActivityEvents', buildAiActivityEvents());
    fixture.componentRef.setInput('result', buildAnalysisResult());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const progressPanel = compiled.querySelector(
      'details.panel-card--progress'
    ) as HTMLDetailsElement | null;
    const copilotPanel = compiled.querySelector(
      'details.ai-workflow-panel'
    ) as HTMLDetailsElement | null;

    expect(progressPanel).not.toBeNull();
    expect(copilotPanel).not.toBeNull();
    expect(progressPanel?.open).toBe(false);
    expect(copilotPanel?.open).toBe(false);

    progressPanel!.open = true;
    progressPanel!.dispatchEvent(new Event('toggle'));
    copilotPanel!.open = true;
    copilotPanel!.dispatchEvent(new Event('toggle'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(progressPanel?.open).toBe(true);
    expect(copilotPanel?.open).toBe(true);
  });

  it('should filter Copilot workflow items by selected event kind', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('aiActivityEvents', buildAiActivityEvents());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    const usageFilter = compiled.querySelector(
      '.ai-workflow-filter input[value="usage"]'
    ) as HTMLInputElement | null;

    expect(usageFilter).not.toBeNull();
    expect(usageFilter?.checked).toBe(true);
    expect(compiled.querySelector('.ai-work-item--usage')).not.toBeNull();

    usageFilter?.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    compiled = fixture.nativeElement as HTMLElement;
    const usageFilterAfterChange = compiled.querySelector(
      '.ai-workflow-filter input[value="usage"]'
    ) as HTMLInputElement | null;

    expect(usageFilterAfterChange?.checked).toBe(false);
    expect(compiled.querySelector('.ai-work-item--usage')).toBeNull();
    expect(compiled.querySelector('.ai-work-item--runtime')).not.toBeNull();
    expect(compiled.querySelector('.ai-work-item--tool')).not.toBeNull();
  });

  it('should use the event summary in the assistant header and full markdown in details', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('aiActivityEvents', [
      {
        eventId: 'event-message-markdown',
        parentEventId: '',
        type: 'assistant.message',
        category: 'MESSAGE',
        status: 'COMPLETED',
        title: 'Wiadomość AI',
        summary: 'AI doprecyzowało hipotezę.',
        turnId: '',
        interactionId: 'interaction-1',
        toolCallId: '',
        toolName: '',
        timestamp: '2026-04-14T12:00:13.800Z',
        details: {
          contentPreview: [
            'Widzę **główną hipotezę** w kodzie.',
            '- pierwszy trop',
            '- drugi trop',
            '- trzeci trop'
          ].join('\n')
        }
      }
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const preview = compiled.querySelector(
      '.ai-work-item__markdown-preview .markdown-content'
    ) as HTMLElement | null;
    const full = compiled.querySelector(
      '.ai-work-item__markdown-full .markdown-content'
    ) as HTMLElement | null;

    expect(preview?.textContent?.trim()).toBe('AI doprecyzowało hipotezę.');
    expect(preview?.textContent).not.toContain('pierwszy trop');
    expect(full?.innerHTML).toContain('<strong>główną hipotezę</strong>');
    expect(full?.textContent).toContain('trzeci trop');
    expect(compiled.querySelector('.ai-work-item__status')).toBeNull();
  });

  it('should use the event summary for the user prompt header', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('aiActivityEvents', [
      {
        eventId: 'event-user-prompt',
        parentEventId: '',
        type: 'user.message',
        category: 'MESSAGE',
        status: 'STARTED',
        title: 'Input do Copilota',
        summary: 'Aplikacja wysłała prompt do sesji Copilota.',
        turnId: '',
        interactionId: '',
        toolCallId: '',
        toolName: '',
        timestamp: '2026-04-14T12:00:13.200Z',
        details: {
          contentPreview: [
            'You are helping with an enterprise software incident analysis.',
            'The result will be read by an operator.'
          ].join('\n')
        }
      }
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const preview = compiled.querySelector(
      '.ai-work-item__markdown-preview .markdown-content'
    ) as HTMLElement | null;

    expect(preview?.textContent?.trim()).toBe('Aplikacja wysłała prompt do sesji Copilota.');
    expect(preview?.textContent).not.toContain('You are helping');
    expect(compiled.querySelector('.ai-work-item__status')).toBeNull();
  });

  it('should hide the prepared prompt on a failed AI step when no result is available', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildFailedAiStep()]);
    fixture.componentRef.setInput('preparedPrompt', buildAiPrompt());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const promptTextarea = compiled.querySelector('.prepared-prompt__textarea');

    expect(compiled.textContent).toContain('Krok zakończył się błędem, więc nie udało się zebrać szczegółów.');
    expect(compiled.textContent).not.toContain('Prompt przygotowany do wysłania do Copilota');
    expect(promptTextarea).toBeNull();
  });

  it('should render GitLab files fetched by AI tools on the final analysis step', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('toolEvidenceSections', [buildAiToolGitLabSection()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const panel = compiled.querySelector('.ai-work-item--tool') as HTMLElement | null;
    const reason = panel?.querySelector('.ai-work-item__text') as HTMLElement | null;
    const icon = panel?.querySelector('.ai-work-item__icon') as HTMLElement | null;
    const status = panel?.querySelector('.ai-work-item__status--done') as HTMLElement | null;

    expect(panel).not.toBeNull();
    expect(reason?.textContent?.trim()).toBe('Sprawdzam fragment klienta z timeoutem.');
    expect(icon?.textContent?.trim()).toBe('code');
    expect(status).not.toBeNull();
    expect(compiled.textContent).toContain('CatalogGatewayClient');
    expect(compiled.textContent).toContain('timeout(Duration.ofSeconds(2))');
    expect(compiled.textContent).not.toContain('Powód pobrania');
    expect(compiled.querySelector('.tool-evidence-timeline')).toBeNull();
  });

  it('should render GitLab discovery tool details on the final analysis step', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('toolEvidenceSections', [buildAiToolGitLabDiscoverySection()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const panel = compiled.querySelector('.ai-work-item--tool') as HTMLElement | null;
    const reason = panel?.querySelector('.ai-work-item__text') as HTMLElement | null;
    const icon = panel?.querySelector('.ai-work-item__icon') as HTMLElement | null;
    const content = compiled.textContent || '';

    expect(reason?.textContent?.trim()).toBe('Szukam kontekstu przepływu wokół repozytorium.');
    expect(icon?.textContent?.trim()).toBe('manage_search');
    expect(content).toContain('Kontekst przepływu');
    expect(content).toContain('AI zebrało 1 kandydata w 1 grupie kontekstu przepływu.');
    expect(content).toContain('repository');
    expect(content).toContain('OrderRepository.java');
    expect(content).toContain('orders-api · src/main/java/com/example/orders/OrderRepository.java · release/2026.04');
    expect(content).toContain('orders-api:src/main/java/com/example/orders/OrderRepository.java');
    expect(compiled.querySelector('.tool-evidence-timeline')).toBeNull();
  });

  it('should render AI tool evidence chronologically in the unified timeline', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('toolEvidenceSections', [
      buildAiToolGitLabSection('2'),
      buildAiToolDatabaseSection('1')
    ]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const reasons = Array.from(
      compiled.querySelectorAll(
        '.ai-work-item--tool .ai-work-item__text'
      )
    ).map((element) => element.textContent?.trim());
    const icons = Array.from(
      compiled.querySelectorAll(
        '.ai-work-item--tool .ai-work-item__icon'
      )
    ).map((element) => element.textContent?.trim());
    const content = compiled.textContent || '';

    expect(reasons).toEqual([
      'Sprawdzam, czy istnieją rekordy dla correlationId.',
      'Sprawdzam fragment klienta z timeoutem.'
    ]);
    expect(icons).toEqual(['storage', 'code']);
    expect(content).toContain('Policzenie rekordów');
    expect(content).toContain('Sprawdzam, czy istnieją rekordy dla correlationId.');
    expect(content).toContain('"tableName": "ORDER_EVENT"');
    expect(content).toContain('"count": 3');
    expect(content).not.toContain('Powód sprawdzenia');
    expect(compiled.querySelector('.tool-evidence-timeline')).toBeNull();

    const toolTooltip = compiled
      .querySelector('.ai-work-item--tool .ai-work-item__technical pre')
      ?.textContent ?? '';
    expect(toolTooltip).toContain('"toolArguments"');
    expect(toolTooltip).toContain('"tableName": "ORDER_EVENT"');
  });
});

function buildDynatraceStep(): AnalysisJobStepResponse {
  return {
    code: 'DYNATRACE_RUNTIME_SIGNALS',
    label: 'Zbieranie danych runtime z Dynatrace',
    status: 'COMPLETED',
    message: 'Zebrano 3 elementow danych.',
    itemCount: 3,
    startedAt: '2026-04-14T12:00:00Z',
    completedAt: '2026-04-14T12:00:04Z'
  };
}

function buildDynatraceSection(): AnalysisEvidenceSection {
  return {
    provider: 'dynatrace',
    category: 'runtime-signals',
    items: [
      {
        title: 'Dynatrace matched service BANKING @ backend-zt002',
        attributes: [
          {
            name: 'displayName',
            value:
              'BANKING @ backend-zt002 || SpringBoot loan-origination-service app || loan-origination-service'
          },
          { name: 'entityId', value: 'SERVICE-ABC123' },
          { name: 'matchScore', value: '315' },
          { name: 'incidentStart', value: '2026-04-11T20:55:00Z' },
          { name: 'incidentEnd', value: '2026-04-11T21:10:00Z' },
          { name: 'matchedNamespaces', value: 'banking-main-zt002' },
          { name: 'matchedPods', value: 'backend-zt002-7fcd60f3b2' },
          { name: 'matchedContainers', value: 'backend' },
          { name: 'matchedServiceNames', value: 'loan-origination-service' }
        ]
      },
      {
        title: 'Dynatrace matched service CustomerController',
        attributes: [
          {
            name: 'displayName',
            value:
              'BANKING @ backend-zt002 || SpringBoot loan-origination-service app || CustomerController'
          },
          { name: 'entityId', value: 'SERVICE-ABC124' },
          { name: 'matchScore', value: '280' },
          { name: 'incidentStart', value: '2026-04-11T20:55:00Z' },
          { name: 'incidentEnd', value: '2026-04-11T21:10:00Z' },
          { name: 'matchedNamespaces', value: 'banking-main-zt002' },
          { name: 'matchedPods', value: 'backend-zt002-7fcd60f3b2' },
          { name: 'matchedContainers', value: 'backend' },
          { name: 'matchedServiceNames', value: 'loan-origination-service' }
        ]
      },
      {
        title: 'Dynatrace matched service ProductController',
        attributes: [
          {
            name: 'displayName',
            value:
              'BANKING @ backend-zt002 || SpringBoot loan-origination-service app || ProductController'
          },
          { name: 'entityId', value: 'SERVICE-ABC125' },
          { name: 'matchScore', value: '240' },
          { name: 'incidentStart', value: '2026-04-11T20:55:00Z' },
          { name: 'incidentEnd', value: '2026-04-11T21:10:00Z' },
          { name: 'matchedNamespaces', value: 'banking-main-zt002' },
          { name: 'matchedPods', value: 'backend-zt002-7fcd60f3b2' },
          { name: 'matchedContainers', value: 'backend' },
          { name: 'matchedServiceNames', value: 'loan-origination-service' }
        ]
      },
      {
        title: 'Dynatrace problem P-230415 Gateway timeout',
        attributes: [
          { name: 'displayId', value: 'P-230415' },
          { name: 'title', value: 'Gateway timeout on backend' },
          { name: 'severityLevel', value: 'ERROR' },
          { name: 'status', value: 'OPEN' },
          { name: 'impactLevel', value: 'SERVICE' },
          { name: 'startTime', value: '2026-04-11T20:57:00Z' },
          { name: 'endTime', value: '2026-04-11T21:06:00Z' },
          { name: 'rootCauseEntityName', value: 'backend-zt002' },
          { name: 'signalCategories', value: 'database-connectivity, availability, messaging' },
          {
            name: 'correlationHighlights',
            value:
              'Failed database connects [DATABASE_CONNECTION_FAILURE] (root cause) || Service unavailable [AVAILABILITY_EVIDENCE] || RabbitMQ Queue Listener lag [QUEUE_BACKLOG]'
          },
          { name: 'affectedEntities', value: 'backend-zt002, payment-service' },
          { name: 'impactedEntities', value: 'payment-api' },
          {
            name: 'evidenceSummary',
            value:
              'Request rate spike, type=METRIC, rootCauseRelevant=true || 5xx increase, type=METRIC, rootCauseRelevant=true'
          }
        ]
      },
      {
        title: 'Dynatrace metric service.response.time.p95 for loan-origination-service',
        attributes: [
          { name: 'entityDisplayName', value: 'loan-origination-service' },
          { name: 'entityId', value: 'SERVICE-ABC123' },
          { name: 'metricId', value: 'builtin:service.response.time' },
          { name: 'metricLabel', value: 'service.response.time.p95' },
          { name: 'unit', value: 'ms' },
          { name: 'resolution', value: '1m' },
          { name: 'queryFrom', value: '2026-04-11T20:55:00Z' },
          { name: 'queryTo', value: '2026-04-11T21:10:00Z' },
          { name: 'nonNullPoints', value: '15' },
          { name: 'minValue', value: '220' },
          { name: 'maxValue', value: '8670' },
          { name: 'averageValue', value: '2540' },
          { name: 'lastValue', value: '8610' }
        ]
      },
      {
        title: 'Dynatrace metric service.response.time.p95 for CustomerController',
        attributes: [
          {
            name: 'entityDisplayName',
            value:
              'BANKING @ backend-zt002 || SpringBoot loan-origination-service app || CustomerController'
          },
          { name: 'entityId', value: 'SERVICE-ABC124' },
          { name: 'metricId', value: 'builtin:service.response.time' },
          { name: 'metricLabel', value: 'service.response.time.p95' },
          { name: 'unit', value: 'ms' },
          { name: 'resolution', value: '1m' },
          { name: 'queryFrom', value: '2026-04-11T20:55:00Z' },
          { name: 'queryTo', value: '2026-04-11T21:10:00Z' },
          { name: 'nonNullPoints', value: '15' },
          { name: 'minValue', value: '220' },
          { name: 'maxValue', value: '12000' },
          { name: 'averageValue', value: '3200' },
          { name: 'lastValue', value: '11800' }
        ]
      },
      {
        title: 'Dynatrace metric service.errors.total.rate for CustomerController',
        attributes: [
          {
            name: 'entityDisplayName',
            value:
              'BANKING @ backend-zt002 || SpringBoot loan-origination-service app || CustomerController'
          },
          { name: 'entityId', value: 'SERVICE-ABC124' },
          { name: 'metricId', value: 'builtin:service.errors.total.count' },
          { name: 'metricLabel', value: 'service.errors.total.rate' },
          { name: 'unit', value: 'count/min' },
          { name: 'resolution', value: '1m' },
          { name: 'queryFrom', value: '2026-04-11T20:55:00Z' },
          { name: 'queryTo', value: '2026-04-11T21:10:00Z' },
          { name: 'nonNullPoints', value: '15' },
          { name: 'minValue', value: '0' },
          { name: 'maxValue', value: '4' },
          { name: 'averageValue', value: '1.3' },
          { name: 'lastValue', value: '2' }
        ]
      }
    ]
  };
}

function buildProgressSteps(): AnalysisJobStepResponse[] {
  return [
    {
      code: 'ELASTICSEARCH_LOGS',
      label: 'Zbieranie logów z Elasticsearch',
      status: 'COMPLETED',
      message: 'Zebrano 2 elementy danych.',
      itemCount: 2,
      startedAt: '2026-04-14T12:00:00Z',
      completedAt: '2026-04-14T12:00:03Z'
    },
    {
      code: 'DYNATRACE_RUNTIME_SIGNALS',
      label: 'Zbieranie danych runtime z Dynatrace',
      status: 'IN_PROGRESS',
      message: 'Pobieranie danych trwa.',
      itemCount: 1,
      startedAt: '2026-04-14T12:00:04Z',
      completedAt: ''
    },
    {
      code: 'GITLAB_RESOLVED_CODE',
      label: 'Zbieranie danych z repozytorium',
      status: 'PENDING',
      message: 'Krok oczekuje na uruchomienie.',
      itemCount: 0,
      startedAt: '',
      completedAt: ''
    }
  ];
}

function buildCompletedSteps(): AnalysisJobStepResponse[] {
  return [
    {
      code: 'ELASTICSEARCH_LOGS',
      label: 'Zbieranie logów z Elasticsearch',
      status: 'COMPLETED',
      message: 'Zebrano 2 elementy danych.',
      itemCount: 2,
      startedAt: '2026-04-14T12:00:00Z',
      completedAt: '2026-04-14T12:00:03Z'
    },
    {
      code: 'DYNATRACE_RUNTIME_SIGNALS',
      label: 'Zbieranie danych runtime z Dynatrace',
      status: 'COMPLETED',
      message: 'Zebrano 130 elementów danych.',
      itemCount: 130,
      startedAt: '2026-04-14T12:00:04Z',
      completedAt: '2026-04-14T12:00:11Z'
    },
    {
      code: 'AI_ANALYSIS',
      label: 'Budowanie końcowej analizy AI',
      status: 'COMPLETED',
      message: 'Analiza zakończona.',
      itemCount: 0,
      startedAt: '2026-04-14T12:00:12Z',
      completedAt: '2026-04-14T12:00:18Z'
    }
  ];
}

function buildOperationalContextStep(): AnalysisJobStepResponse {
  return {
    code: 'OPERATIONAL_CONTEXT',
    label: 'Dopasowanie kontekstu operacyjnego',
    status: 'COMPLETED',
    message: 'Krok zakończony bez nowych danych.',
    itemCount: 0,
    startedAt: '2026-04-14T12:00:10Z',
    completedAt: '2026-04-14T12:00:12Z'
  };
}

function buildUnavailableDynatraceSection(): AnalysisEvidenceSection {
  return {
    provider: 'dynatrace',
    category: 'runtime-signals',
    items: [
      {
        title: 'Dynatrace collection status',
        attributes: [
          { name: 'dynatraceItemType', value: 'collection-status' },
          { name: 'collectionStatus', value: 'UNAVAILABLE' },
          { name: 'collectionReason', value: 'Dynatrace API request failed with HTTP 502' },
          {
            name: 'interpretation',
            value:
              'Dynatrace visibility is unavailable for this incident. Missing Dynatrace metrics, problems, or component signals must be treated as lack of data, not as healthy runtime.'
          },
          { name: 'correlationStatus', value: 'UNKNOWN' }
        ]
      }
    ]
  };
}

function buildCompletedAiStep(): AnalysisJobStepResponse {
  return {
    code: 'AI_ANALYSIS',
    label: 'Budowanie końcowej analizy AI',
    status: 'COMPLETED',
    message: 'Analiza zakończona.',
    itemCount: 0,
    startedAt: '2026-04-14T12:00:12Z',
    completedAt: '2026-04-14T12:00:18Z'
  };
}

function buildCompletedAiStepWithUsage(): AnalysisJobStepResponse {
  return {
    ...buildCompletedAiStep(),
    usage: {
      inputTokens: 2400,
      outputTokens: 420,
      cacheReadTokens: 300,
      cacheWriteTokens: 50,
      totalTokens: 2820,
      cost: 2.3,
      apiDurationMs: 1100,
      apiCallCount: 1,
      model: 'gpt-5.4',
      contextTokenLimit: 128000,
      contextCurrentTokens: 9200,
      contextMessages: 6
    }
  };
}

function buildFailedAiStep(): AnalysisJobStepResponse {
  return {
    code: 'AI_ANALYSIS',
    label: 'Budowanie końcowej analizy AI',
    status: 'FAILED',
    message: 'AI gateway timeout',
    itemCount: 0,
    startedAt: '2026-04-14T12:00:12Z',
    completedAt: '2026-04-14T12:00:18Z'
  };
}

function buildAiPrompt(): string {
  return `You are helping with a software incident analysis.

correlationId: timeout-123
environment: zt002
gitLabBranch: release/2026.04
gitLabGroup: platform/backend

Provider: dynatrace, category: runtime-signals
- Dynatrace problem P-230415 Gateway timeout | signalCategories=database-connectivity, availability`;
}

function buildAiToolGitLabSection(captureOrder = '1'): AnalysisEvidenceSection {
  return {
    provider: 'gitlab',
    category: 'tool-fetched-code',
    items: [
      {
        title: 'edge-client-service file src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java',
        attributes: [
          {
            name: 'filePath',
            value: 'src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java'
          },
          { name: 'reason', value: 'Sprawdzam fragment klienta z timeoutem.' },
          { name: 'toolCaptureOrder', value: captureOrder },
          { name: 'startLine', value: '5' },
          {
            name: 'content',
            value:
              'public class CatalogGatewayClient {\n    void configure() {\n        timeout(Duration.ofSeconds(2));\n    }\n}'
          }
        ]
      }
    ]
  };
}

function buildAiToolDatabaseSection(captureOrder = '2'): AnalysisEvidenceSection {
  return {
    provider: 'database',
    category: 'tool-results',
    items: [
      {
        title: 'db_count_rows',
        attributes: [
          { name: 'reason', value: 'Sprawdzam, czy istnieją rekordy dla correlationId.' },
          { name: 'toolCaptureOrder', value: captureOrder },
          { name: 'toolName', value: 'db_count_rows' },
          { name: 'toolCallId', value: 'tool-call-db-1' },
          {
            name: 'toolArguments',
            value: `{
  "reason": "Sprawdzam, czy istnieją rekordy dla correlationId.",
  "tableName": "ORDER_EVENT",
  "filters": {
    "CORRELATION_ID": "corr-123"
  }
}`
          },
          {
            name: 'result',
            value: `{
  "environment": "zt002",
  "databaseAlias": "oracle",
  "table": {
    "schema": "CLP",
    "tableName": "ORDER_EVENT"
  },
  "count": 3,
  "appliedFilters": ["CORRELATION_ID = corr-123"],
  "warnings": []
}`
          }
        ]
      }
    ]
  };
}

function buildAiActivityEvents(): AnalysisAiActivityEvent[] {
  return [
    {
      eventId: 'event-turn-1',
      parentEventId: '',
      type: 'assistant.turn_start',
      category: 'TURN',
      status: 'STARTED',
      title: 'Turn AI rozpoczęty',
      summary: 'Copilot rozpoczął turn turn-1.',
      turnId: 'turn-1',
      interactionId: 'interaction-1',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:12Z',
      details: { turnId: 'turn-1', interactionId: 'interaction-1' }
    },
    {
      eventId: 'event-context-1',
      parentEventId: '',
      type: 'session.usage_info',
      category: 'CONTEXT',
      status: 'INFO',
      title: 'Kontekst sesji',
      summary: 'Kontekst: 9200/128000 tokenów, 6 wiadomości.',
      turnId: '',
      interactionId: '',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:13Z',
      details: { tokenLimit: 128000, currentTokens: 9200, messagesLength: 6 }
    },
    {
      eventId: 'event-usage-1',
      parentEventId: '',
      type: 'assistant.usage',
      category: 'USAGE',
      status: 'INFO',
      title: 'Zużycie modelu',
      summary: 'Model: gpt-5.4, input 12000, cache 1200, output 300 tokenów.',
      turnId: '',
      interactionId: '',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:13.500Z',
      details: {
        model: 'gpt-5.4',
        inputTokens: 12000,
        cacheReadTokens: 1200,
        outputTokens: 300,
        durationMs: 2400
      }
    },
    {
      eventId: 'event-reasoning-1',
      parentEventId: '',
      type: 'assistant.reasoning',
      category: 'MESSAGE',
      status: 'INFO',
      title: 'Rozumowanie AI',
      summary: 'Najpierw analizuję stack trace i brakujący kontekst kodu.',
      turnId: '',
      interactionId: '',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:13.600Z',
      details: {
        reasoningId: 'reasoning-1',
        contentPreview: 'Najpierw analizuję stack trace i brakujący kontekst kodu.'
      }
    },
    {
      eventId: 'event-message-1',
      parentEventId: '',
      type: 'assistant.message',
      category: 'MESSAGE',
      status: 'COMPLETED',
      title: 'Wiadomość AI',
      summary: 'Copilot poprosił o 1 wywołanie narzędzia.',
      turnId: '',
      interactionId: 'interaction-1',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:13.800Z',
      details: {
        contentPreview: '',
        reasoningTextPreview: 'Sprawdzam repozytorium przed uruchomieniem toola.',
        toolRequestCount: 1,
        toolRequests: [
          {
            toolCallId: 'tool-call-db-1',
            name: 'db_count_rows',
            arguments: {
              tableName: 'ORDER_EVENT',
              reason: 'Sprawdzam liczbę rekordów dla correlationId.'
            }
          }
        ]
      }
    },
    {
      eventId: 'event-tool-1',
      parentEventId: '',
      type: 'tool.execution_start',
      category: 'TOOL',
      status: 'STARTED',
      title: 'Tool start: db_count_rows',
      summary: 'Copilot uruchamia db_count_rows.',
      turnId: '',
      interactionId: '',
      toolCallId: 'tool-call-db-1',
      toolName: 'db_count_rows',
      timestamp: '2026-04-14T12:00:14Z',
      details: {
        toolCallId: 'tool-call-db-1',
        toolName: 'db_count_rows',
        arguments: { tableName: 'ORDER_EVENT' }
      }
    },
    {
      eventId: 'event-turn-2',
      parentEventId: '',
      type: 'assistant.turn_end',
      category: 'TURN',
      status: 'COMPLETED',
      title: 'Turn AI zakończony',
      summary: 'Copilot zakończył turn turn-1.',
      turnId: 'turn-1',
      interactionId: '',
      toolCallId: '',
      toolName: '',
      timestamp: '2026-04-14T12:00:15Z',
      details: { turnId: 'turn-1' }
    }
  ];
}

function buildAiToolGitLabDiscoverySection(captureOrder = '3'): AnalysisEvidenceSection {
  return {
    provider: 'gitlab',
    category: 'tool-discovery',
    items: [
      {
        title: 'GitLab flow context',
        attributes: [
          { name: 'toolName', value: 'gitlab_find_flow_context' },
          { name: 'reason', value: 'Szukam kontekstu przepływu wokół repozytorium.' },
          { name: 'toolCaptureOrder', value: captureOrder },
          { name: 'group', value: 'platform/backend' },
          { name: 'branch', value: 'release/2026.04' },
          { name: 'groupCount', value: '1' },
          { name: 'candidateCount', value: '1' },
          { name: 'recommendedNextReadCount', value: '1' },
          {
            name: 'groups',
            value: JSON.stringify([
              {
                role: 'repository',
                candidates: [
                  {
                    projectName: 'orders-api',
                    branch: 'release/2026.04',
                    filePath: 'src/main/java/com/example/orders/OrderRepository.java',
                    matchReason: 'Dopasowanie po nazwie encji i repozytorium.',
                    matchScore: 42,
                    inferredRole: 'repository',
                    recommendedReadStrategy: 'chunk',
                    preview: 'interface OrderRepository'
                  }
                ]
              }
            ])
          },
          {
            name: 'recommendedNextReads',
            value: JSON.stringify(['orders-api:src/main/java/com/example/orders/OrderRepository.java'])
          }
        ]
      }
    ]
  };
}

function buildAnalysisResult() {
  return {
    status: 'COMPLETED',
    correlationId: 'timeout-123',
    environment: 'zt002',
    gitLabBranch: 'release/2026.04',
    detectedProblem: 'Gateway timeout on backend',
    affectedProcess: 'Obsługa klienta',
    affectedBoundedContext: 'Customer Context',
    affectedTeam: 'Customer Team',
    functionalAnalysis: 'Backend zwraca timeout przy obsłudze żądania kredytowego w procesie obsługi klienta.',
    technicalAnalysis: 'Sprawdź połączenia backendu z bazą danych oraz timeouty w `CustomerController`.',
    confidence: 'medium',
    visibilityLimits: ['Brak potwierdzenia po stronie bazy danych.'],
    prompt: buildAiPrompt(),
    usage: null
  };
}
