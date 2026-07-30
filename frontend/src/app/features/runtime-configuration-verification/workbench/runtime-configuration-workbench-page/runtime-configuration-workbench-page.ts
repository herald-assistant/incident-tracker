import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import {
  RuntimeConfigurationBranchCoverage,
  RuntimeConfigurationDeepContext,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationMode,
  RuntimeConfigurationWorkbenchPreviewRequest,
  RuntimeConfigurationWorkbenchPreviewResponse,
  SanitizedConfigurationNode
} from '../../models/runtime-configuration-verification.models';
import { RuntimeConfigurationVerificationApiService } from '../../services/runtime-configuration-verification-api.service';

type PreviewStatus = 'idle' | 'loading' | 'success' | 'error';
type Perspective = 'source' | 'mapping' | 'anonymization' | 'ai' | 'deep';
type CopyTarget = 'request' | 'response' | 'prompt' | 'artifact';

interface MappingRow {
  role: string;
  documentIndex: number;
  depth: number;
  node: SanitizedConfigurationNode;
}

interface SourceFileRow {
  side: 'SOURCE' | 'TARGET';
  branch: string;
  branchExists: boolean;
  role: string;
  path: string;
  status: string;
  commitId: string | null;
  lastModifiedAt: string | null;
  sizeBytes: number | null;
  errorCode: string | null;
}

const ENDPOINT = '/api/runtime-configuration-verification/workbench/preview';
const MAX_RENDERED_ROWS = 500;
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
    'Wybierz scope, aby prześledzić dokładnie ten sam bezpieczny pipeline co analiza.'
  );
  readonly durationMs = signal<number | null>(null);
  readonly response = signal<RuntimeConfigurationWorkbenchPreviewResponse | null>(null);
  readonly requestJson = signal('');
  readonly responseJson = signal('');
  readonly selectedPerspective = signal<Perspective>('source');
  readonly selectedArtifactName = signal('');
  readonly copiedTarget = signal<CopyTarget | null>(null);

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
    { id: 'mapping', label: 'Mapping', detail: 'Struktura i różnice', tag: 'DERIVED' },
    { id: 'anonymization', label: 'Anonymization', detail: 'Decyzje ochrony', tag: 'AI-SAFE' },
    { id: 'ai', label: 'AI input', detail: 'Prompt i artefakty', tag: 'AI-SAFE' },
    { id: 'deep', label: 'DEEP scope', detail: 'Kod i ownership', tag: 'DERIVED' }
  ];

  readonly hasResult = computed(() => this.status() !== 'idle');
  readonly sourceRows = computed(() => this.buildSourceRows(this.response()?.sourceAcquisition));
  readonly mappingRows = computed(() => this.buildMappingRows(this.response()));
  readonly mappingRowsTruncated = computed(
    () => this.totalMappingNodes() > this.mappingRows().length
  );
  readonly totalMappingNodes = computed(
    () =>
      this.response()?.mapping.documents.reduce(
        (total, document) => total + this.countNodes(document.root),
        0
      ) ?? 0
  );
  readonly anonymizationRows = computed(
    () => this.response()?.anonymization.decisions.slice(0, MAX_RENDERED_ROWS) ?? []
  );
  readonly anonymizationRowsTruncated = computed(
    () =>
      (this.response()?.anonymization.decisions.length ?? 0) >
      this.anonymizationRows().length
  );
  readonly selectedArtifactContent = computed(() => {
    const result = this.response();
    return result?.artifactContents[this.selectedArtifactName()] ?? '';
  });
  readonly deepContext = computed<RuntimeConfigurationDeepContext | null>(
    () => this.response()?.deepContext ?? null
  );
  readonly branchPairInvalid = (): boolean =>
    !!this.form.controls.sourceBranch.value &&
    this.form.controls.sourceBranch.value === this.form.controls.targetBranch.value;

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
    this.requestJson.set(requestJson);
    this.responseJson.set('');
    this.response.set(null);
    this.status.set('loading');
    this.statusCode.set(null);
    this.durationMs.set(null);
    this.message.set('Pobieramy metadata i przygotowujemy bezpieczny snapshot dla AI…');

    this.api
      .preview(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.response.set(response);
          this.responseJson.set(this.toJson(response));
          this.selectedArtifactName.set(response.artifacts[0]?.name ?? '');
          this.status.set('success');
          this.statusCode.set(200);
          this.durationMs.set(Date.now() - startedAt);
          this.message.set(
            response.visibilityLimits.length
              ? `Preview gotowy z ${response.visibilityLimits.length} ograniczeniem/ograniczeniami widoczności.`
              : 'Preview gotowy. AI nie zostało uruchomione.'
          );
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
    this.response.set(null);
    this.requestJson.set('');
    this.responseJson.set('');
    this.status.set('idle');
    this.statusCode.set(null);
    this.durationMs.set(null);
    this.message.set('Scope został zresetowany. Pipeline nie został uruchomiony.');
    this.selectedPerspective.set('source');
    this.selectedArtifactName.set('');
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
      status === 'success' ? 'done' : status === 'loading' ? 'running' : status === 'error' ? 'error' : 'queued'
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

  private buildSourceRows(
    acquisition: RuntimeConfigurationWorkbenchPreviewResponse['sourceAcquisition'] | undefined
  ): SourceFileRow[] {
    if (!acquisition) {
      return [];
    }
    return [
      ...this.coverageRows('SOURCE', acquisition.source),
      ...this.coverageRows('TARGET', acquisition.target)
    ];
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
      branchExists: coverage.branchExists,
      role: file.role,
      path: file.path,
      status: file.status,
      commitId: file.commitId ?? file.lastCommitId,
      lastModifiedAt: file.lastModifiedAt,
      sizeBytes: file.sizeBytes,
      errorCode: file.errorCode
    }));
  }

  private buildMappingRows(
    response: RuntimeConfigurationWorkbenchPreviewResponse | null
  ): MappingRow[] {
    if (!response) {
      return [];
    }
    const rows: MappingRow[] = [];
    for (const document of response.mapping.documents) {
      this.appendMappingNode(rows, document.role, document.documentIndex, document.root, 0);
      if (rows.length >= MAX_RENDERED_ROWS) {
        break;
      }
    }
    return rows.slice(0, MAX_RENDERED_ROWS);
  }

  private appendMappingNode(
    rows: MappingRow[],
    role: string,
    documentIndex: number,
    node: SanitizedConfigurationNode,
    depth: number
  ): void {
    if (rows.length >= MAX_RENDERED_ROWS) {
      return;
    }
    rows.push({ role, documentIndex, node, depth });
    for (const child of node.children) {
      this.appendMappingNode(rows, role, documentIndex, child, depth + 1);
    }
  }

  private countNodes(node: SanitizedConfigurationNode): number {
    return 1 + node.children.reduce((total, child) => total + this.countNodes(child), 0);
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
