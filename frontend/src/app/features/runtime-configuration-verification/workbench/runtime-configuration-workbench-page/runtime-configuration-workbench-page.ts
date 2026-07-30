import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import {
  RuntimeConfigurationBranchCoverage,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationMode,
  RuntimeConfigurationWorkbenchAiInputResponse,
  RuntimeConfigurationWorkbenchAnonymizationPage,
  RuntimeConfigurationWorkbenchArtifactResponse,
  RuntimeConfigurationWorkbenchDeepResponse,
  RuntimeConfigurationWorkbenchMappingPage,
  RuntimeConfigurationWorkbenchPreviewRequest,
  RuntimeConfigurationWorkbenchPreviewResponse,
  RuntimeConfigurationWorkbenchSourceResponse
} from '../../models/runtime-configuration-verification.models';
import { RuntimeConfigurationVerificationApiService } from '../../services/runtime-configuration-verification-api.service';

type PreviewStatus = 'idle' | 'loading' | 'success' | 'error';
type Perspective = 'source' | 'mapping' | 'anonymization' | 'ai' | 'deep';
type CopyTarget = 'request' | 'response' | 'prompt' | 'artifact';

interface SourceFileRow {
  side: 'SOURCE' | 'TARGET';
  branch: string;
  role: string;
  path: string;
  status: string;
  commitId: string | null;
  lastModifiedAt: string | null;
  sizeBytes: number | null;
  errorCode: string | null;
}

const ENDPOINT = '/api/runtime-configuration-verification/workbench/preview';
const PAGE_SIZE = 100;
const EMPTY_OPTIONS: RuntimeConfigurationVerificationInputOptions = {
  modes: ['BASIC', 'DEEP'],
  branches: [],
  repositories: [],
  systems: []
};

@Component({
  selector: 'app-runtime-configuration-workbench-page',
  imports: [ReactiveFormsModule],
  templateUrl: './runtime-configuration-workbench-page.html',
  styleUrls: [
    '../../../source-console-layout.scss',
    './runtime-configuration-workbench-page.scss'
  ]
})
export class RuntimeConfigurationWorkbenchPageComponent {
  private readonly api = inject(RuntimeConfigurationVerificationApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly options = signal(EMPTY_OPTIONS);
  readonly optionsLoading = signal(true);
  readonly optionsError = signal('');
  readonly status = signal<PreviewStatus>('idle');
  readonly statusCode = signal<number | null>(null);
  readonly message = signal(
    'Wybierz scope, aby utworzyć lekki, tymczasowy i sanitizowany preview.'
  );
  readonly durationMs = signal<number | null>(null);
  readonly summary = signal<RuntimeConfigurationWorkbenchPreviewResponse | null>(null);
  readonly requestJson = signal('');
  readonly responseJson = signal('');
  readonly selectedPerspective = signal<Perspective>('source');
  readonly copiedTarget = signal<CopyTarget | null>(null);
  readonly detailLoading = signal<Perspective | 'artifact' | 'prompt' | null>(null);
  readonly detailError = signal('');

  readonly sourceDetail = signal<RuntimeConfigurationWorkbenchSourceResponse | null>(null);
  readonly mappingPage = signal<RuntimeConfigurationWorkbenchMappingPage | null>(null);
  readonly mappingChangedOnly = signal(true);
  readonly anonymizationPage =
    signal<RuntimeConfigurationWorkbenchAnonymizationPage | null>(null);
  readonly deepDetail = signal<RuntimeConfigurationWorkbenchDeepResponse | null>(null);
  readonly aiInput = signal<RuntimeConfigurationWorkbenchAiInputResponse | null>(null);
  readonly artifact = signal<RuntimeConfigurationWorkbenchArtifactResponse | null>(null);

  readonly form = new FormGroup({
    mode: new FormControl<RuntimeConfigurationVerificationMode>('BASIC', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    repositoryId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    systemId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    sourceBranch: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    targetBranch: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    codeRef: new FormControl('', { nonNullable: true })
  });

  readonly perspectives: ReadonlyArray<{
    id: Perspective;
    label: string;
    detail: string;
    tag: string;
  }> = [
    { id: 'source', label: 'Source acquisition', detail: 'Pliki i metadata', tag: 'FETCHED METADATA' },
    { id: 'mapping', label: 'Mapping', detail: 'Zmiany, stronicowane', tag: 'DERIVED' },
    { id: 'anonymization', label: 'Anonymization', detail: 'Decyzje, stronicowane', tag: 'AI-SAFE' },
    { id: 'ai', label: 'AI input', detail: 'Ładowany na żądanie', tag: 'AI-SAFE' },
    { id: 'deep', label: 'DEEP scope', detail: 'Kod i ownership', tag: 'DERIVED' }
  ];

  readonly hasResult = computed(() => this.status() !== 'idle');
  readonly sourceRows = computed(() => {
    const detail = this.sourceDetail();
    return detail
      ? [
          ...this.coverageRows('SOURCE', detail.source),
          ...this.coverageRows('TARGET', detail.target)
        ]
      : [];
  });
  readonly initialPayloadCharacters = computed(() => this.responseJson().length);

  constructor() {
    this.api
      .getInputOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => {
          this.options.set(options);
          this.applyDefaults(options);
          this.optionsLoading.set(false);
        },
        error: () => {
          this.optionsError.set('Nie udało się pobrać allowlistowanego scope z backendu.');
          this.optionsLoading.set(false);
        }
      });
  }

  selectMode(mode: RuntimeConfigurationVerificationMode): void {
    this.form.controls.mode.setValue(mode);
  }

  selectPerspective(perspective: Perspective): void {
    this.selectedPerspective.set(perspective);
    this.detailError.set('');
    const previewId = this.summary()?.previewId;
    if (!previewId) {
      return;
    }
    if (perspective === 'source' && !this.sourceDetail()) {
      this.loadSource(previewId);
    } else if (perspective === 'mapping' && !this.mappingPage()) {
      this.loadMapping(0);
    } else if (perspective === 'anonymization' && !this.anonymizationPage()) {
      this.loadAnonymization(0);
    } else if (perspective === 'deep' && !this.deepDetail()) {
      this.loadDeep(previewId);
    }
  }

  submit(event: Event): void {
    event.preventDefault();
    if (this.form.invalid || this.branchPairInvalid()) {
      this.form.markAllAsTouched();
      this.status.set('error');
      this.message.set(
        this.branchPairInvalid()
          ? 'Wybierz dwa różne branche środowiskowe.'
          : 'Uzupełnij wszystkie wymagane pola scope.'
      );
      return;
    }

    const value = this.form.getRawValue();
    const payload: RuntimeConfigurationWorkbenchPreviewRequest = {
      mode: value.mode,
      repositoryId: value.repositoryId,
      systemId: value.systemId,
      sourceBranch: value.sourceBranch,
      targetBranch: value.targetBranch,
      ...(value.codeRef.trim() ? { codeRef: value.codeRef.trim() } : {})
    };
    const requestJson = this.toJson({ endpoint: ENDPOINT, method: 'POST', body: payload });
    const startedAt = Date.now();
    this.clearSnapshotDetails();
    this.requestJson.set(requestJson);
    this.responseJson.set('');
    this.summary.set(null);
    this.status.set('loading');
    this.statusCode.set(null);
    this.durationMs.set(null);
    this.message.set('Budujemy sanitizowany snapshot i jego kompaktowy model AI…');

    this.api
      .preview(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.responseJson.set(this.toJson(summary));
          this.status.set('success');
          this.statusCode.set(200);
          this.durationMs.set(Date.now() - startedAt);
          this.message.set(
            `Lekki preview gotowy. Szczegóły wygasną ${new Date(summary.expiresAt).toLocaleTimeString()}.`
          );
          this.loadSource(summary.previewId);
        },
        error: (error: unknown) => {
          const normalized = this.normalizeError(error);
          this.status.set('error');
          this.statusCode.set(normalized.status);
          this.durationMs.set(Date.now() - startedAt);
          this.message.set(normalized.message);
          this.responseJson.set(this.toJson(normalized.body));
        }
      });
  }

  reset(): void {
    this.applyDefaults(this.options());
    this.clearSnapshotDetails();
    this.summary.set(null);
    this.requestJson.set('');
    this.responseJson.set('');
    this.status.set('idle');
    this.statusCode.set(null);
    this.durationMs.set(null);
    this.message.set('Scope został zresetowany. Pipeline nie został uruchomiony.');
    this.selectedPerspective.set('source');
  }

  setMappingChangedOnly(value: boolean): void {
    this.mappingChangedOnly.set(value);
    this.loadMapping(0);
  }

  previousMappingPage(): void {
    const page = this.mappingPage();
    if (page) {
      this.loadMapping(Math.max(0, page.offset - page.limit));
    }
  }

  nextMappingPage(): void {
    const page = this.mappingPage();
    if (page && page.offset + page.items.length < page.totalItems) {
      this.loadMapping(page.offset + page.limit);
    }
  }

  previousAnonymizationPage(): void {
    const page = this.anonymizationPage();
    if (page) {
      this.loadAnonymization(Math.max(0, page.offset - page.limit));
    }
  }

  nextAnonymizationPage(): void {
    const page = this.anonymizationPage();
    if (page && page.offset + page.items.length < page.totalItems) {
      this.loadAnonymization(page.offset + page.limit);
    }
  }

  loadAiInput(): void {
    const previewId = this.summary()?.previewId;
    if (!previewId || this.detailLoading()) {
      return;
    }
    this.detailLoading.set('prompt');
    this.detailError.set('');
    this.api
      .getWorkbenchAiInput(previewId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.aiInput.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  loadArtifact(name: string): void {
    const previewId = this.summary()?.previewId;
    if (!previewId || this.detailLoading()) {
      return;
    }
    this.detailLoading.set('artifact');
    this.detailError.set('');
    this.api
      .getWorkbenchArtifact(previewId, name)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.artifact.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  branchPairInvalid(): boolean {
    return (
      !!this.form.controls.sourceBranch.value &&
      this.form.controls.sourceBranch.value === this.form.controls.targetBranch.value
    );
  }

  statusLabel(status: PreviewStatus): string {
    return {
      idle: 'Gotowe do testu',
      loading: 'W toku',
      success: 'OK',
      error: 'Błąd'
    }[status];
  }

  statusPillClass(status: PreviewStatus): string {
    return `status-pill status-pill--${
      status === 'success'
        ? 'done'
        : status === 'loading'
          ? 'running'
          : status === 'error'
            ? 'error'
            : 'queued'
    }`;
  }

  durationLabel(value: number | null): string {
    if (value === null) {
      return 'n/a';
    }
    return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(1)} s`;
  }

  sizeLabel(value: number | null): string {
    if (value === null) {
      return 'n/a';
    }
    return value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KB`;
  }

  async copy(target: CopyTarget, value: string): Promise<void> {
    if (!value || !(await copyTextToClipboard(value))) {
      return;
    }
    this.copiedTarget.set(target);
    window.setTimeout(() => {
      if (this.copiedTarget() === target) {
        this.copiedTarget.set(null);
      }
    }, 1600);
  }

  private loadSource(previewId: string): void {
    this.detailLoading.set('source');
    this.api
      .getWorkbenchSource(previewId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.sourceDetail.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  private loadMapping(offset: number): void {
    const previewId = this.summary()?.previewId;
    if (!previewId) {
      return;
    }
    this.detailLoading.set('mapping');
    this.detailError.set('');
    this.api
      .getWorkbenchMapping(previewId, offset, PAGE_SIZE, this.mappingChangedOnly())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.mappingPage.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  private loadAnonymization(offset: number): void {
    const previewId = this.summary()?.previewId;
    if (!previewId) {
      return;
    }
    this.detailLoading.set('anonymization');
    this.detailError.set('');
    this.api
      .getWorkbenchAnonymization(previewId, offset, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.anonymizationPage.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  private loadDeep(previewId: string): void {
    this.detailLoading.set('deep');
    this.detailError.set('');
    this.api
      .getWorkbenchDeep(previewId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.deepDetail.set(response);
          this.detailLoading.set(null);
        },
        error: (error) => this.detailFailed(error)
      });
  }

  private detailFailed(error: unknown): void {
    const normalized = this.normalizeError(error);
    this.detailError.set(normalized.message);
    this.detailLoading.set(null);
  }

  private clearSnapshotDetails(): void {
    this.sourceDetail.set(null);
    this.mappingPage.set(null);
    this.anonymizationPage.set(null);
    this.deepDetail.set(null);
    this.aiInput.set(null);
    this.artifact.set(null);
    this.detailLoading.set(null);
    this.detailError.set('');
    this.mappingChangedOnly.set(true);
  }

  private applyDefaults(options: RuntimeConfigurationVerificationInputOptions): void {
    const branches = options.branches;
    const sourceBranch = branches.includes('dev1') ? 'dev1' : (branches[0] ?? '');
    const targetBranch =
      branches.find((branch) => branch !== sourceBranch && branch.startsWith('zt')) ??
      branches.find((branch) => branch !== sourceBranch) ??
      '';
    this.form.reset({
      mode: options.modes[0] ?? 'BASIC',
      repositoryId: options.repositories[0]?.id ?? '',
      systemId: options.systems[0]?.id ?? '',
      sourceBranch,
      targetBranch,
      codeRef: ''
    });
  }

  private coverageRows(
    side: SourceFileRow['side'],
    coverage: RuntimeConfigurationBranchCoverage | null
  ): SourceFileRow[] {
    if (!coverage) {
      return [];
    }
    return coverage.files.map((file) => ({
      side,
      branch: coverage.branch,
      role: file.role,
      path: file.path,
      status: file.status,
      commitId: file.commitId ?? file.lastCommitId,
      lastModifiedAt: file.lastModifiedAt,
      sizeBytes: file.sizeBytes,
      errorCode: file.errorCode
    }));
  }

  private normalizeError(error: unknown): {
    status: number | null;
    message: string;
    body: unknown;
  } {
    if (error instanceof HttpErrorResponse) {
      const payload =
        error.error && typeof error.error === 'object' && !Array.isArray(error.error)
          ? (error.error as Record<string, unknown>)
          : {};
      const code = typeof payload['code'] === 'string' ? payload['code'] : 'REQUEST_FAILED';
      const message =
        typeof payload['message'] === 'string' && payload['message'].trim()
          ? payload['message']
          : `Preview zakończył się błędem HTTP ${error.status}.`;
      return {
        status: error.status || null,
        message,
        body: { code, message, status: error.status || null }
      };
    }
    return {
      status: null,
      message: 'Nie udało się uzyskać odpowiedzi z backendu.',
      body: { code: 'REQUEST_FAILED', message: 'Brak bezpiecznej odpowiedzi HTTP.' }
    };
  }

  private toJson(value: unknown): string {
    return JSON.stringify(value, null, 2) ?? 'null';
  }
}
