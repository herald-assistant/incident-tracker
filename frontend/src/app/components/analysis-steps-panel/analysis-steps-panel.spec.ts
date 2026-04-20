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

    expect(compiled.textContent).toContain('GitLab · Pliki pobrane przez AI');
    expect(compiled.textContent).toContain('CatalogGatewayClient');
    expect(compiled.textContent).toContain('edge-client-service');
    expect(compiled.textContent).toContain('timeout(Duration.ofSeconds(2))');
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

function buildAiToolGitLabSection(): AnalysisEvidenceSection {
  return {
    provider: 'gitlab',
    category: 'tool-fetched-code',
    items: [
      {
        title: 'edge-client-service file src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java',
        attributes: [
          { name: 'group', value: 'sample/runtime' },
          { name: 'projectName', value: 'edge-client-service' },
          { name: 'branch', value: 'release/2026.04' },
          {
            name: 'filePath',
            value: 'src/main/java/com/example/synthetic/edge/CatalogGatewayClient.java'
          },
          { name: 'referenceType', value: 'AI_TOOL_FILE_CHUNK' },
          { name: 'toolName', value: 'gitlab_read_repository_file_chunk' },
          { name: 'requestedStartLine', value: '5' },
          { name: 'requestedEndLine', value: '12' },
          { name: 'returnedStartLine', value: '5' },
          { name: 'returnedEndLine', value: '12' },
          { name: 'totalLines', value: '14' },
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
    prompt: buildAiPrompt()
  };
}
