import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';

import {
  OperationalContextDeleteEvent,
  OperationalContextDeleteImpact,
  OperationalContextEditableEntity,
  OperationalContextEditorState,
  OperationalContextFieldError,
  OperationalContextMaintenanceCapabilities,
  OperationalContextMaintenanceError,
  OperationalContextMutationEvent,
  OperationalContextPayload,
  OperationalContextWritableType
} from '../models/operational-context-maintenance.models';
import { OperationalContextMaintenanceApiService } from './operational-context-maintenance-api.service';

@Injectable({ providedIn: 'root' })
export class OperationalContextMaintenanceFacade {
  private readonly api = inject(OperationalContextMaintenanceApiService);
  private readonly savedSubject = new Subject<OperationalContextMutationEvent>();
  private readonly deletedSubject = new Subject<OperationalContextDeleteEvent>();

  readonly capabilities = signal<OperationalContextMaintenanceCapabilities | null>(null);
  readonly capabilitiesLoading = signal(false);
  readonly capabilitiesError = signal('');
  readonly editor = signal<OperationalContextEditorState | null>(null);
  readonly editorLoading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly fieldErrors = signal<OperationalContextFieldError[]>([]);
  readonly deleteImpact = signal<OperationalContextDeleteImpact | null>(null);
  readonly deleteLoading = signal(false);
  readonly deleteError = signal('');

  readonly writable = computed(() => this.capabilities() !== null);
  readonly saved$ = this.savedSubject.asObservable();
  readonly deleted$ = this.deletedSubject.asObservable();

  loadCapabilities(): void {
    this.capabilitiesLoading.set(true);
    this.capabilitiesError.set('');
    this.api.getCapabilities().subscribe({
      next: (capabilities) => {
        this.capabilities.set(capabilities);
        this.capabilitiesLoading.set(false);
      },
      error: () => {
        this.capabilities.set(null);
        this.capabilitiesError.set('The local operational context copy is unavailable.');
        this.capabilitiesLoading.set(false);
      }
    });
  }

  supports(type: string): type is OperationalContextWritableType {
    return Boolean(
      this.writable() && this.capabilities()?.supportedEntityTypes.includes(type as OperationalContextWritableType)
    );
  }

  openCreate(type: OperationalContextWritableType): void {
    if (!this.supports(type)) {
      return;
    }
    this.resetOperationState();
    this.editor.set({
      mode: 'create',
      type,
      entity: {
        type,
        id: '',
        sourceFile: '',
        payload: this.emptyPayload(type)
      }
    });
  }

  openEdit(type: OperationalContextWritableType, id: string): void {
    if (!this.supports(type) || !id) {
      return;
    }
    this.resetOperationState();
    this.editorLoading.set(true);
    this.api.getEntity(type, id).subscribe({
      next: (entity) => {
        this.editor.set({ mode: 'edit', type, entity });
        this.editorLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.error.set(this.apiError(error).message || `Could not load ${type}/${id} for editing.`);
        this.editorLoading.set(false);
      }
    });
  }

  closeEditor(): void {
    this.editor.set(null);
    this.resetOperationState();
  }

  save(payload: OperationalContextPayload): void {
    const state = this.editor();
    if (!state || this.busy()) {
      return;
    }
    const id = String(payload['id'] || '').trim();
    const request = { type: state.type, id, payload };
    this.busy.set(true);
    this.error.set('');
    this.fieldErrors.set([]);
    const operation = state.mode === 'create'
      ? this.api.create(request)
      : this.api.update(request);
    operation.subscribe({
      next: (result) => {
        this.busy.set(false);
        this.editor.set(null);
        this.savedSubject.next({ action: state.mode === 'edit' ? 'update' : 'create', entity: result.entity });
      },
      error: (error: HttpErrorResponse) => {
        const apiError = this.apiError(error);
        this.busy.set(false);
        this.error.set(apiError.message || 'Could not save operational context entity.');
        this.fieldErrors.set(apiError.fieldErrors || []);
      }
    });
  }

  requestDelete(type: OperationalContextWritableType, id: string): void {
    if (!this.supports(type) || !id) {
      return;
    }
    this.deleteImpact.set(null);
    this.deleteError.set('');
    this.deleteLoading.set(true);
    this.api.getDeleteImpact(type, id).subscribe({
      next: (impact) => {
        this.deleteImpact.set(impact);
        this.deleteLoading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.deleteError.set(this.apiError(error).message || `Could not assess delete impact for ${type}/${id}.`);
        this.deleteLoading.set(false);
      }
    });
  }

  cancelDelete(): void {
    this.deleteImpact.set(null);
    this.deleteError.set('');
    this.deleteLoading.set(false);
  }

  confirmDelete(): void {
    const impact = this.deleteImpact();
    if (!impact?.allowed || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.deleteError.set('');
    this.api.delete(impact.type, impact.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleteImpact.set(null);
        this.deletedSubject.next({ type: impact.type, id: impact.id });
      },
      error: (error: HttpErrorResponse) => {
        const apiError = this.apiError(error);
        this.busy.set(false);
        this.deleteError.set(apiError.message || 'Could not delete operational context entity.');
      }
    });
  }

  private resetOperationState(): void {
    this.editorLoading.set(false);
    this.busy.set(false);
    this.error.set('');
    this.fieldErrors.set([]);
  }

  private emptyPayload(type: OperationalContextWritableType): OperationalContextPayload {
    if (type === 'glossary-term') return { id: '', term: '', category: '' };
    if (type === 'handoff-rule') return { id: '', title: '' };
    return { id: '', name: '' };
  }

  private apiError(error: HttpErrorResponse): OperationalContextMaintenanceError {
    const body = error.error as Partial<OperationalContextMaintenanceError> | null;
    return {
      code: String(body?.code || `HTTP_${error.status}`),
      message: String(body?.message || ''),
      fieldErrors: Array.isArray(body?.fieldErrors) ? body.fieldErrors : []
    };
  }
}
