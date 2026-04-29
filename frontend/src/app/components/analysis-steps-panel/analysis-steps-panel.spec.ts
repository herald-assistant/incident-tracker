import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import {
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
    expect(usagePill?.textContent?.trim()).toBe('Tokeny 2 820');
    expect(tooltip).toContain('Wejście: 2 400');
    expect(tooltip).toContain('Wyjście: 420');
    expect(tooltip).toContain('Model: gpt-5.4');
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
    const panel = compiled.querySelector('.tool-evidence-panel') as HTMLElement | null;
    const reason = compiled.querySelector('.tool-evidence-panel__reason') as HTMLElement | null;
    const icon = compiled.querySelector('.tool-evidence-panel__source-icon') as HTMLElement | null;

    expect(panel).not.toBeNull();
    expect(panel?.classList.contains('mat-expanded')).toBeFalsy();
    expect(reason?.textContent?.trim()).toBe('Sprawdzam fragment klienta z timeoutem.');
    expect(icon?.textContent?.trim()).toBe('code');
    expect(icon?.getAttribute('aria-label')).toBe('GitLab');
    expect(compiled.textContent).toContain('CatalogGatewayClient');
    expect(compiled.textContent).toContain('GitLab');
    expect(compiled.textContent).toContain('timeout(Duration.ofSeconds(2))');
    expect(compiled.textContent).not.toContain('Powód pobrania');
  });

  it('should render GitLab discovery tool details on the final analysis step', async () => {
    const fixture = TestBed.createComponent(AnalysisStepsPanelComponent);
    fixture.componentRef.setInput('steps', [buildCompletedAiStep()]);
    fixture.componentRef.setInput('toolEvidenceSections', [buildAiToolGitLabDiscoverySection()]);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const reason = compiled.querySelector('.tool-evidence-panel__reason') as HTMLElement | null;
    const icon = compiled.querySelector('.tool-evidence-panel__source-icon') as HTMLElement | null;
    const content = compiled.textContent || '';

    expect(reason?.textContent?.trim()).toBe('Szukam kontekstu przepływu wokół repozytorium.');
    expect(icon?.textContent?.trim()).toBe('manage_search');
    expect(icon?.getAttribute('aria-label')).toBe('GitLab');
    expect(content).toContain('Kontekst przepływu');
    expect(content).toContain('AI zebrało 1 kandydata w 1 grupie kontekstu przepływu.');
    expect(content).toContain('repository');
    expect(content).toContain('OrderRepository.java');
    expect(content).toContain('orders-api · src/main/java/com/example/orders/OrderRepository.java · release/2026.04');
    expect(content).toContain('orders-api:src/main/java/com/example/orders/OrderRepository.java');
  });

  it('should render AI tool evidence as one chronological accordion', async () => {
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
    const reasons = Array.from(compiled.querySelectorAll('.tool-evidence-panel__reason')).map(
      (element) => element.textContent?.trim()
    );
    const icons = Array.from(compiled.querySelectorAll('.tool-evidence-panel__source-icon')).map(
      (element) => element.textContent?.trim()
    );
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
    summary: 'Backend zwraca timeout przy obsłudze żądania kredytowego.',
    detectedProblem: 'Gateway timeout on backend',
    recommendedAction: 'Sprawdź połączenia backendu z bazą danych.',
    rationale: 'Wzrost czasu odpowiedzi koreluje z błędami połączenia do bazy.',
    affectedFunction: 'CustomerController',
    affectedProcess: 'Obsługa klienta',
    affectedBoundedContext: 'Customer Context',
    affectedTeam: 'Customer Team',
    prompt: buildAiPrompt(),
    usage: null
  };
}
