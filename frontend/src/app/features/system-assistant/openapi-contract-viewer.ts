import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AssistantApiDefinition, OpenApiDocument } from './system-assistant.models';
import { SystemAssistantApi } from './system-assistant-api.service';
import { OpenApiRenderer } from './openapi-renderer';

@Component({
  selector: 'app-openapi-contract-viewer',
  imports: [OpenApiRenderer],
  template: `
    <header><div><p>Podgląd kontraktu · OpenAPI</p><h2 id="openApiViewerTitle">{{contract.name}}</h2></div>
      <button type="button" class="secondary-button viewer-close" aria-label="Zamknij podgląd kontraktu" (click)="dialog.close()">Zamknij</button></header>
    @if (loading()) { <p role="status">Przygotowywanie dokumentacji…</p> }
    @if (error()) { <p role="alert">{{error()}}</p><button type="button" class="secondary-button viewer-close" (click)="load()">Spróbuj ponownie</button> }
    @if (document()) {
      <app-openapi-renderer [specification]="document()!.specification" [title]="'Dokumentacja OpenAPI — ' + contract.name"
        (closeRequested)="dialog.close()" />
    }
  `,
  styles: `
    :host{display:flex;flex-direction:column;box-sizing:border-box;width:min(1240px,96vw);height:92dvh;padding:16px;gap:12px;background:var(--color-surface);color:var(--color-text);border:1px solid var(--color-border);border-radius:14px;box-shadow:var(--shadow-card)}
    header{display:flex;justify-content:space-between;align-items:start;gap:12px}header div{min-width:0}header p{margin:0;color:var(--color-muted);font-size:.8rem}h2{font-size:1.15rem;font-weight:500;margin:4px 0 0;overflow-wrap:anywhere}
    .viewer-close{width:auto;flex:0 0 auto}
    app-openapi-renderer{flex:1;min-height:0}[role=alert]{color:var(--color-danger)}
    @media(max-width:650px){:host{padding:10px;height:94dvh}h2{font-size:1rem}}
  `
})
export class OpenApiContractViewer implements OnInit {
  readonly contract = inject<AssistantApiDefinition>(DIALOG_DATA);
  readonly dialog = inject<DialogRef<void>>(DialogRef);
  private readonly api = inject(SystemAssistantApi);
  private readonly destroyRef = inject(DestroyRef);
  readonly document = signal<OpenApiDocument | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  ngOnInit(): void { this.load(); }
  load(): void {
    this.document.set(null); this.loading.set(true); this.error.set('');
    this.api.documentOpenApi(this.contract.id, this.contract.yaml).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: document => { this.document.set(document); this.loading.set(false); },
      error: error => { this.loading.set(false); this.error.set(error.error?.message || 'Nie udało się przygotować podglądu kontraktu.'); }
    });
  }
}
