import { Component, DestroyRef, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { OpenApiSelectionChange, OpenApiSelectionRow } from './system-assistant.models';

/** Shared by the two System Assistant dialogs; the parent owns all selection state. */
@Component({
  selector: 'app-openapi-renderer',
  template: `<iframe #viewer src="/openapi-viewer/index.html" sandbox="allow-scripts" referrerpolicy="no-referrer"
    [title]="title()" (load)="loaded()"></iframe>`,
  styles: `:host{display:flex;min-height:0}iframe{width:100%;flex:1;min-height:0;border:0;background:#fff;border-radius:6px}`
})
export class OpenApiRenderer {
  readonly specification = input.required<Record<string, unknown>>();
  readonly selection = input<OpenApiSelectionRow[] | null>(null);
  readonly title = input('Dokumentacja OpenAPI');
  readonly selectionChange = output<OpenApiSelectionChange>();
  readonly closeRequested = output<void>();
  private readonly frame = viewChild<ElementRef<HTMLIFrameElement>>('viewer');
  private readonly browser = inject(DOCUMENT).defaultView;
  private readonly ready = signal(false);
  private lastSpecification?: Record<string, unknown>;

  constructor() {
    this.browser?.addEventListener('message', this.receive);
    inject(DestroyRef).onDestroy(() => this.browser?.removeEventListener('message', this.receive));
    effect(() => { if (this.ready()) this.send(this.specification(), this.selection()); });
  }
  loaded(): void {
    this.lastSpecification = undefined;
    this.ready.set(true);
    this.send(this.specification(), this.selection());
  }
  private send(specification: Record<string, unknown>, selection: OpenApiSelectionRow[] | null): void {
    // The opaque sandbox origin requires '*'; only this exact child window receives data.
    const message = specification !== this.lastSpecification
      ? {type: 'TDW_OPENAPI_DOCUMENT', specification, selection}
      : {type: 'TDW_OPENAPI_SELECTION_STATE', selection};
    this.frame()?.nativeElement.contentWindow?.postMessage(message, '*');
    this.lastSpecification = specification;
  }
  private readonly receive = (event: MessageEvent): void => {
    if (event.source !== this.frame()?.nativeElement.contentWindow || !event.data || typeof event.data !== 'object') return;
    const data = event.data;
    if (data.type === 'TDW_OPENAPI_VIEWER_CLOSE' && Object.keys(data).length === 1) this.closeRequested.emit();
    if (data.type !== 'TDW_OPENAPI_SELECTION_CHANGE' || Object.keys(data).length !== 4 || typeof data.checked !== 'boolean') return;
    const candidate = this.selection()?.find(item => item.method === data.method && item.path === data.path);
    if (candidate && !candidate.disabled) this.selectionChange.emit({method: candidate.method, path: candidate.path, checked: data.checked});
  };
}
