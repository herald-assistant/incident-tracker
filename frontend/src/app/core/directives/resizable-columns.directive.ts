import {
  AfterViewInit,
  Directive,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Renderer2,
  SimpleChanges,
  inject
} from '@angular/core';

export interface ResizableColumnConfig {
  id: string;
  defaultTrack: string;
  minWidth: number;
  resizable?: boolean;
}

const STORAGE_KEY_PREFIX = 'analysis-ui:resizable-columns:';
const RESIZING_BODY_CLASS = 'is-column-resizing';

@Directive({
  selector: '[appResizableColumnsHost]',
  standalone: true,
  exportAs: 'appResizableColumnsHost'
})
export class ResizableColumnsHostDirective implements OnInit, OnChanges {
  @Input({ required: true }) appResizableColumnsHost = '';
  @Input() appResizableColumns: readonly ResizableColumnConfig[] = [];

  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly widthOverrides = new Map<string, number>();

  ngOnInit(): void {
    this.restoreWidths();
    this.applyGridTemplate();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['appResizableColumnsHost'] && !changes['appResizableColumns']) {
      return;
    }

    this.restoreWidths();
    this.applyGridTemplate();
  }

  isColumnResizable(columnId: string): boolean {
    return this.findColumn(columnId)?.resizable !== false;
  }

  setColumnWidth(columnId: string, nextWidth: number): void {
    const column = this.findColumn(columnId);
    if (!column || column.resizable === false) {
      return;
    }

    const clampedWidth = Math.max(column.minWidth, Math.round(nextWidth));
    this.widthOverrides.set(columnId, clampedWidth);
    this.persistWidths();
    this.applyGridTemplate();
  }

  resetColumnWidth(columnId: string): void {
    if (!this.widthOverrides.has(columnId)) {
      return;
    }

    this.widthOverrides.delete(columnId);
    this.persistWidths();
    this.applyGridTemplate();
  }

  private findColumn(columnId: string): ResizableColumnConfig | undefined {
    return this.appResizableColumns.find((column) => column.id === columnId);
  }

  private restoreWidths(): void {
    this.widthOverrides.clear();

    if (!this.appResizableColumnsHost || !this.supportsLocalStorage()) {
      return;
    }

    try {
      const serialized = window.localStorage.getItem(this.storageKey());
      if (!serialized) {
        return;
      }

      const parsed = JSON.parse(serialized) as Record<string, unknown>;
      for (const column of this.appResizableColumns) {
        const value = parsed[column.id];
        if (typeof value !== 'number' || !Number.isFinite(value)) {
          continue;
        }

        this.widthOverrides.set(column.id, Math.max(column.minWidth, Math.round(value)));
      }
    } catch {
      // Ignore invalid persisted state and keep default widths.
    }
  }

  private persistWidths(): void {
    if (!this.appResizableColumnsHost || !this.supportsLocalStorage()) {
      return;
    }

    if (this.widthOverrides.size === 0) {
      window.localStorage.removeItem(this.storageKey());
      return;
    }

    const serialized = JSON.stringify(Object.fromEntries(this.widthOverrides));
    window.localStorage.setItem(this.storageKey(), serialized);
  }

  private applyGridTemplate(): void {
    const hostElement = this.elementRef.nativeElement;

    if (this.appResizableColumns.length === 0) {
      hostElement.style.removeProperty('--resizable-columns-template');
      return;
    }

    const template = this.appResizableColumns
      .map((column) => {
        const width = this.widthOverrides.get(column.id);
        return typeof width === 'number' ? `${width}px` : column.defaultTrack;
      })
      .join(' ');

    hostElement.style.setProperty('--resizable-columns-template', template);
  }

  private storageKey(): string {
    return `${STORAGE_KEY_PREFIX}${this.appResizableColumnsHost}`;
  }

  private supportsLocalStorage(): boolean {
    return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined';
  }
}

@Directive({
  selector: '[appResizableColumn]',
  standalone: true
})
export class ResizableColumnDirective implements AfterViewInit, OnDestroy {
  @Input({ required: true }) appResizableColumn = '';
  @Input() appResizableColumnHost: ResizableColumnsHostDirective | null = null;

  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly renderer = inject(Renderer2);
  private readonly injectedHost = inject(ResizableColumnsHostDirective, { optional: true });

  private readonly cleanupCallbacks: Array<() => void> = [];
  private dragCleanupCallbacks: Array<() => void> = [];

  ngAfterViewInit(): void {
    const host = this.resolveHost();
    if (!host || !this.appResizableColumn) {
      return;
    }

    const headerCell = this.elementRef.nativeElement;
    this.renderer.addClass(headerCell, 'resizable-column');

    if (!host.isColumnResizable(this.appResizableColumn)) {
      return;
    }

    const handle = this.renderer.createElement('span');
    this.renderer.addClass(handle, 'resizable-column__handle');
    this.renderer.setAttribute(handle, 'aria-hidden', 'true');
    this.renderer.appendChild(headerCell, handle);

    this.cleanupCallbacks.push(
      this.renderer.listen(handle, 'pointerdown', (event: PointerEvent) => this.startResize(event)),
      this.renderer.listen(handle, 'dblclick', (event: MouseEvent) => this.resetWidth(event))
    );
  }

  ngOnDestroy(): void {
    this.clearActiveDrag();

    for (const cleanup of this.cleanupCallbacks) {
      cleanup();
    }

    this.cleanupCallbacks.length = 0;
  }

  private startResize(event: PointerEvent): void {
    const host = this.resolveHost();
    if (!host || event.button !== 0) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    this.clearActiveDrag();

    const headerCell = this.elementRef.nativeElement;
    const ownerDocument = headerCell.ownerDocument;
    const body = ownerDocument.body;
    const initialWidth = headerCell.getBoundingClientRect().width;
    const startX = event.clientX;

    body.classList.add(RESIZING_BODY_CLASS);

    this.dragCleanupCallbacks = [
      this.renderer.listen('window', 'pointermove', (moveEvent: PointerEvent) => {
        moveEvent.preventDefault();
        const nextWidth = initialWidth + (moveEvent.clientX - startX);
        host.setColumnWidth(this.appResizableColumn, nextWidth);
      }),
      this.renderer.listen('window', 'pointerup', () => this.clearActiveDrag()),
      this.renderer.listen('window', 'pointercancel', () => this.clearActiveDrag())
    ];
  }

  private resetWidth(event: MouseEvent): void {
    const host = this.resolveHost();
    if (!host) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();
    host.resetColumnWidth(this.appResizableColumn);
  }

  private resolveHost(): ResizableColumnsHostDirective | null {
    return this.appResizableColumnHost ?? this.injectedHost;
  }

  private clearActiveDrag(): void {
    const ownerDocument = this.elementRef.nativeElement.ownerDocument;
    ownerDocument.body.classList.remove(RESIZING_BODY_CLASS);

    for (const cleanup of this.dragCleanupCallbacks) {
      cleanup();
    }

    this.dragCleanupCallbacks = [];
  }
}
