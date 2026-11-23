import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TextFieldComponent } from '../../components/text-field/text-field';
import { ChoiceSelectComponent } from '../../components/choice-select/choice-select';
import { SystemAssistantApi } from './system-assistant-api.service';
import { OpenApiCandidate, OpenApiPrepared, OpenApiPreview, OpenApiSelectionChange } from './system-assistant.models';
import { OpenApiRenderer } from './openapi-renderer';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface OpenApiPickerData { id: string; yaml: string; sourceName: string; }
export interface OpenApiPickerResult extends OpenApiPrepared { title: string; }

@Component({
  selector: 'app-openapi-operation-picker',
  imports: [TextFieldComponent, ChoiceSelectComponent, OpenApiRenderer, MatTooltipModule],
  templateUrl: './openapi-operation-picker.html',
  styleUrl: './openapi-operation-picker.scss'
})
export class OpenApiOperationPicker implements OnInit {
  readonly data = inject<OpenApiPickerData>(DIALOG_DATA);
  readonly dialog = inject<DialogRef<OpenApiPickerResult>>(DialogRef);
  private readonly api = inject(SystemAssistantApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly preview = signal<OpenApiPreview | null>(null);
  readonly loading = signal(true);
  readonly applying = signal(false);
  readonly error = signal('');
  readonly contractName = signal('');
  readonly query = signal('');
  readonly method = signal('');
  readonly tag = signal('');
  readonly onlySelected = signal(false);
  readonly selected = signal(new Set<string>());
  readonly selectedWarnings = computed(() => [...new Set((this.preview()?.operations ?? [])
    .filter(operation => this.selected().has(this.key(operation))).flatMap(operation => operation.warnings))]);
  readonly selectableOperations = computed(() => (this.preview()?.operations ?? []).filter(operation => !operation.unavailableReason));
  readonly page = signal(0);
  readonly pageSize = 50;
  readonly methods = computed(() => [{value: '', label: 'Wszystkie metody'},
    ...[...new Set(this.preview()?.operations.map(operation => operation.method) ?? [])].sort().map(value => ({value, label: value}))]);
  readonly tags = computed(() => [{value: '', label: 'Wszystkie tagi'},
    ...[...new Set(this.preview()?.operations.flatMap(operation => operation.tags) ?? [])].sort().map(value => ({value, label: value}))]);
  readonly filtered = computed(() => {
    const query = this.query().trim().toLocaleLowerCase();
    return (this.preview()?.operations ?? []).filter(operation =>
      (!this.method() || operation.method === this.method()) && (!this.tag() || operation.tags.includes(this.tag()))
      && (!this.onlySelected() || this.selected().has(this.key(operation)))
      && (!query || `${operation.path} ${operation.operationId} ${operation.summary} ${operation.tags.join(' ')}`.toLocaleLowerCase().includes(query)));
  });
  readonly pages = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));
  readonly currentPage = computed(() => Math.min(this.page(), this.pages() - 1));
  readonly rows = computed(() => this.filtered().slice(this.currentPage() * this.pageSize, (this.currentPage() + 1) * this.pageSize));
  readonly renderedSpecification = computed<Record<string, unknown>>(() => {
    const source = this.preview()!.specification;
    const sourcePaths = source['paths'] as Record<string, Record<string, unknown>>;
    const methods = new Set(['get', 'post', 'put', 'delete', 'patch', 'head', 'options', 'trace']);
    const paths: Record<string, Record<string, unknown>> = Object.create(null);
    for (const operation of this.rows()) {
      const original = sourcePaths[operation.path];
      if (!paths[operation.path]) paths[operation.path] = Object.fromEntries(Object.entries(original).filter(([key]) => !methods.has(key)));
      paths[operation.path][operation.method.toLowerCase()] = original[operation.method.toLowerCase()];
    }
    const activeTags = new Set(this.rows().flatMap(operation => operation.tags));
    const declaredTags = source['tags'];
    const tags = Array.isArray(declaredTags)
      ? {tags: declaredTags.filter(tag => tag && typeof tag === 'object' && activeTags.has(tag['name']))}
      : {};
    return {...source, paths, ...tags};
  });
  readonly renderedSelection = computed(() => this.rows().map(operation => ({method: operation.method, path: operation.path,
    checked: this.selected().has(this.key(operation)), disabled: this.applying() || !!operation.unavailableReason, unavailableReason: operation.unavailableReason})));
  readonly exceedsLimit = computed(() => this.selected().size > (this.preview()?.maxSelected ?? 60));
  readonly canApply = computed(() => !this.loading() && !this.applying() && this.contractName().trim().length > 0 && this.contractName().length <= 100 && this.selected().size > 0 && !this.exceedsLimit());

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true); this.error.set('');
    this.api.previewOpenApi(this.data.id, this.data.yaml).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: preview => {
        this.preview.set(preview); this.loading.set(false);
        this.contractName.set((preview.title || this.data.sourceName.replace(/\.ya?ml$/i, '') || 'Nowy kontrakt').slice(0, 100));
      },
      error: error => { this.loading.set(false); this.error.set(this.errorMessage(error)); }
    });
  }
  key(operation: {method: string; path: string}): string { return JSON.stringify([operation.method, operation.path]); }
  changeSelection(change: OpenApiSelectionChange): void {
    const operation = this.preview()?.operations.find(item => this.key(item) === this.key(change));
    if (operation) this.toggle(operation, change.checked);
  }
  toggle(operation: OpenApiCandidate, checked: boolean): void {
    if (operation.unavailableReason || this.applying()) return;
    this.selected.update(current => {
      const next = new Set(current); if (checked) next.add(this.key(operation)); else next.delete(this.key(operation)); return next;
    });
  }
  selectAll(): void {
    if (this.applying()) return;
    this.selected.set(new Set(this.selectableOperations().map(operation => this.key(operation))));
  }
  resetSelection(): void { if (!this.applying()) this.selected.set(new Set()); }
  apply(): void {
    if (!this.canApply()) return;
    this.applying.set(true); this.error.set('');
    const operations = this.preview()!.operations.filter(operation => this.selected().has(this.key(operation)))
      .map(({method, path}) => ({method, path}));
    this.api.selectOpenApi(this.data.id, this.data.yaml, operations).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => this.dialog.close({...result, title: this.contractName().trim()}),
      error: error => { this.applying.set(false); this.error.set(this.errorMessage(error)); }
    });
  }
  private errorMessage(error: {error?: {message?: string}}): string { return error?.error?.message || 'Nie udało się przygotować operacji. Spróbuj ponownie.'; }
}
