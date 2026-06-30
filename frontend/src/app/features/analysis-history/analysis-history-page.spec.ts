import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';

import {
  AnalysisExportEnvelope,
  AnalysisJobStateSnapshot,
  LocalAnalysisRunDetailResponse,
  LocalAnalysisRunListItemResponse,
  LocalAnalysisRunListResponse,
  RenameLocalAnalysisRunRequest
} from '../../core/models/analysis.models';
import { AnalysisRunHistoryApiService } from '../../core/services/analysis-run-history-api.service';
import { buildExportEnvelope } from '../../core/utils/analysis-import-export.utils';
import { AnalysisHistoryPageComponent } from './analysis-history-page';

describe('AnalysisHistoryPageComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should render the local index and keep full run JSON unloaded until user action', async () => {
    const { fixture, historyApi } = await createComponent();

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.listRuns).toHaveBeenCalledTimes(1);
    expect(historyApi.getRun).not.toHaveBeenCalled();
    expect(historyApi.exportRun).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Catalog corr-123');
    expect(fixture.nativeElement.textContent).toContain('Flow /customers goal');
    expect(fixture.nativeElement.textContent).toContain('Zakończona');
  });

  it('should filter runs only by name and feature', async () => {
    const { fixture } = await createComponent();

    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentInstance.filterControl.setValue('flow');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Catalog corr-123');
    expect(fixture.nativeElement.textContent).toContain('Flow /customers goal');

    fixture.componentInstance.filterControl.setValue('catalog');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Catalog corr-123');
    expect(fixture.nativeElement.textContent).not.toContain('Flow /customers goal');
  });

  it('should open an incident run in the feature screen without loading full run JSON in history', async () => {
    const { fixture, historyApi, router } = await createComponent();
    const run = listRuns()[0]!;
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance.openRun(run);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.getRun).not.toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/incident-analysis'], {
      queryParams: { localRunId: 'analysis-1' }
    });
    expect(fixture.nativeElement.textContent).not.toContain('Analiza funkcjonalna procesu katalogowego.');
  });

  it('should route a flow run to the Flow Explorer feature screen', async () => {
    const { fixture, router } = await createComponent();
    const run = listRuns()[1]!;
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture.componentInstance.openRun(run);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigateSpy).toHaveBeenCalledWith(['/flow-explorer'], {
      queryParams: { localRunId: 'analysis-2' }
    });
  });

  it('should export the stored envelope without rebuilding the payload', async () => {
    const { fixture, historyApi } = await createComponent();
    const run = listRuns()[0]!;
    const detail = detailForRun(run);
    const exportEnvelope = detail.exportEnvelope;
    const createObjectUrlSpy = vi.fn((_: Blob | MediaSource) => 'blob:analysis-export');
    const revokeObjectUrlSpy = vi.fn((_: string) => undefined);
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const stringifySpy = vi.spyOn(JSON, 'stringify');
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: createObjectUrlSpy
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: revokeObjectUrlSpy
    });

    historyApi.exportRun.mockReturnValueOnce(of(exportEnvelope));
    fixture.componentInstance.exportRun(run);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(historyApi.exportRun).toHaveBeenCalledWith('analysis-1');
    expect(historyApi.getRun).not.toHaveBeenCalled();
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1);
    expect(clickSpy).toHaveBeenCalledTimes(1);
    expect(stringifySpy).toHaveBeenCalledWith(exportEnvelope, null, 2);
  });

  it('should rename a run and update the index row', async () => {
    const { fixture, historyApi } = await createComponent();
    const run = listRuns()[0]!;
    const renamedDetail = {
      ...detailForRun(run),
      name: 'Nowa nazwa catalog'
    };
    historyApi.renameRun.mockReturnValueOnce(of(renamedDetail));

    fixture.componentInstance.startRename(run);
    fixture.componentInstance.renameControl.setValue('Nowa nazwa catalog');
    fixture.componentInstance.saveRename(new Event('submit'), run);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.renameRun).toHaveBeenCalledWith('analysis-1', { name: 'Nowa nazwa catalog' });
    expect(fixture.nativeElement.textContent).toContain('Nowa nazwa catalog');
    expect(fixture.nativeElement.textContent).not.toContain('Catalog corr-123');
  });

  it('should delete a confirmed local run from the list', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, historyApi } = await createComponent();
    const run = listRuns()[0]!;

    fixture.componentInstance.deleteRun(run);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(confirmSpy).toHaveBeenCalled();
    expect(historyApi.deleteRun).toHaveBeenCalledWith('analysis-1');
    expect(fixture.nativeElement.textContent).not.toContain('Catalog corr-123');
    expect(fixture.nativeElement.textContent).toContain('Flow /customers goal');
  });

  async function createComponent() {
    const historyApi = {
      listRuns: vi.fn<() => Observable<LocalAnalysisRunListResponse>>(() =>
        of({ runs: listRuns() })
      ),
      getRun: vi.fn<(analysisId: string) => Observable<LocalAnalysisRunDetailResponse>>(
        (analysisId) => of(detailForRun(listRuns().find((run) => run.analysisId === analysisId)!))
      ),
      exportRun: vi.fn<(analysisId: string) => Observable<unknown>>((analysisId) =>
        of(detailForRun(listRuns().find((run) => run.analysisId === analysisId)!).exportEnvelope)
      ),
      renameRun:
        vi.fn<
          (
            analysisId: string,
            request: RenameLocalAnalysisRunRequest
          ) => Observable<LocalAnalysisRunDetailResponse>
        >((analysisId, request) =>
          of({
            ...detailForRun(listRuns().find((run) => run.analysisId === analysisId)!),
            name: request.name
          })
        ),
      deleteRun: vi.fn<() => Observable<void>>(() => of(undefined))
    };

    await TestBed.configureTestingModule({
      imports: [AnalysisHistoryPageComponent],
      providers: [
        provideLocationMocks(),
        provideRouter([]),
        { provide: AnalysisRunHistoryApiService, useValue: historyApi }
      ]
    }).compileComponents();

    return {
      fixture: TestBed.createComponent(AnalysisHistoryPageComponent),
      historyApi,
      router: TestBed.inject(Router)
    };
  }
});

function listRuns(): LocalAnalysisRunListItemResponse[] {
  return [
    {
      analysisId: 'analysis-1',
      feature: 'incident-analysis',
      name: 'Catalog corr-123',
      status: 'COMPLETED',
      createdAt: '2026-05-02T10:00:00Z',
      updatedAt: '2026-05-02T10:04:00Z',
      completedAt: '2026-05-02T10:04:00Z'
    },
    {
      analysisId: 'analysis-2',
      feature: 'flow-explorer',
      name: 'Flow /customers goal',
      status: 'COMPLETED',
      createdAt: '2026-05-02T09:00:00Z',
      updatedAt: '2026-05-02T09:04:00Z',
      completedAt: '2026-05-02T09:04:00Z'
    }
  ];
}

function detailForRun(run: LocalAnalysisRunListItemResponse): LocalAnalysisRunDetailResponse {
  const job = completedJob(run.analysisId);
  const exportEnvelope: AnalysisExportEnvelope = buildExportEnvelope(
    job,
    '2026-05-02T10:05:00Z'
  );

  return {
    ...run,
    exportEnvelope,
    continuationEnabled: true
  };
}

function completedJob(analysisId: string): AnalysisJobStateSnapshot {
  return {
    analysisId,
    correlationId: analysisId === 'analysis-1' ? 'corr-123' : '/customers goal',
    aiModel: 'gpt-5.4',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: 'AI_ANALYSIS',
    currentStepLabel: 'Budowanie końcowej analizy AI',
    environment: 'dev3',
    gitLabBranch: 'main',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-05-02T10:00:00Z',
    updatedAt: '2026-05-02T10:04:00Z',
    completedAt: '2026-05-02T10:04:00Z',
    steps: [
      {
        code: 'AI_ANALYSIS',
        label: 'Budowanie końcowej analizy AI',
        status: 'COMPLETED',
        message: 'Gotowe',
        itemCount: 1,
        startedAt: '2026-05-02T10:03:00Z',
        completedAt: '2026-05-02T10:04:00Z',
        consumesEvidence: [],
        producesEvidence: []
      }
    ],
    evidenceSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    chatMessages: [
      {
        id: 'chat-user-1',
        role: 'USER',
        status: 'COMPLETED',
        content: 'Przygotuj opis do Jiry.',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:02:00Z',
        updatedAt: '2026-05-02T10:02:00Z',
        completedAt: '2026-05-02T10:02:00Z',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      },
      {
        id: 'chat-assistant-1',
        role: 'ASSISTANT',
        status: 'COMPLETED',
        content: 'Opis do Jiry.',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:03:00Z',
        updatedAt: '2026-05-02T10:03:00Z',
        completedAt: '2026-05-02T10:03:00Z',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      }
    ],
    preparedPrompt: 'Prompt bez tokenów.',
    result: {
      status: 'COMPLETED',
      correlationId: 'corr-123',
      environment: 'dev3',
      gitLabBranch: 'main',
      detectedProblem: 'DOWNSTREAM_TIMEOUT',
      affectedProcess: 'Catalog',
      affectedBoundedContext: 'Catalog Context',
      affectedTeam: 'Catalog Team',
      functionalAnalysis: 'Analiza funkcjonalna procesu katalogowego.',
      technicalAnalysis: 'Analiza techniczna timeoutu w kliencie katalogu.',
      confidence: 'medium',
      visibilityLimits: ['Brak potwierdzenia po stronie downstream.'],
      prompt: 'Prompt bez tokenów.',
      usage: null
    }
  };
}
