import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { UxInspectorCaptureSnapshot } from '../models/ux-inspector.models';
import { UxInspectorApiService } from './ux-inspector-api.service';

@Injectable()
export class UxInspectorCaptureIngressService {
  private readonly api = inject(UxInspectorApiService);
  private readonly destroyRef = inject(DestroyRef);
  private requestId = 0;

  readonly snapshot = signal<UxInspectorCaptureSnapshot | null>(null);
  readonly capture = signal<UxInspectorCaptureSnapshot['capture'] | null>(null);
  readonly status = signal<'idle' | 'loading' | 'received' | 'invalid'>('idle');
  readonly error = signal('');
  readonly storeStatus = signal<'unavailable' | 'available'>('unavailable');
  readonly formStatus = signal<'unavailable' | 'available'>('unavailable');
  readonly formFields = signal<UxInspectorCaptureSnapshot['formFields']['fields'] | null>(null);

  load(captureId: string): void {
    const id = captureId.trim();
    const current = ++this.requestId;
    this.clear();
    if (!id) return;
    this.status.set('loading');
    this.api.getCapture(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (snapshot) => {
        if (current !== this.requestId) return;
        if (snapshot.captureId !== id || snapshot.capture.captureId !== id) {
          this.fail('Odpowiedź capture nie pasuje do identyfikatora w adresie.');
          return;
        }
        this.snapshot.set(snapshot);
        this.capture.set(snapshot.capture);
        this.storeStatus.set(snapshot.store.status === 'AVAILABLE' ? 'available' : 'unavailable');
        this.formStatus.set(snapshot.formFields.status === 'AVAILABLE' ? 'available' : 'unavailable');
        this.formFields.set(snapshot.formFields.status === 'AVAILABLE' ? snapshot.formFields.fields : null);
        this.status.set('received');
      },
      error: (error: HttpErrorResponse) => {
        if (current !== this.requestId) return;
        this.fail(error.status === 404
          ? 'Capture nie istnieje w pamięci TDW. Wskaż element ponownie w Browser Tools.'
          : 'Nie udało się pobrać capture. Spróbuj ponownie.');
      }
    });
  }

  consumeCapture(): void {
    ++this.requestId;
    this.clear();
  }

  private clear(): void {
    this.snapshot.set(null);
    this.capture.set(null);
    this.storeStatus.set('unavailable');
    this.formStatus.set('unavailable');
    this.formFields.set(null);
    this.status.set('idle');
    this.error.set('');
  }

  private fail(message: string): void {
    this.clear();
    this.status.set('invalid');
    this.error.set(message);
  }
}
