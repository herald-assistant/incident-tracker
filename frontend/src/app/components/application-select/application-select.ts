import { Component, computed, HostListener, input, output, signal } from '@angular/core';

export interface ApplicationOption {
  systemId: string; name: string | null; summary?: string | null; aliases?: string[]; searchTerms?: string[];
}
@Component({
  selector: 'app-application-select',
  template: `
    <div class="application-select" (click)="$event.stopPropagation()">
      <span class="field-label">Application</span>
      <button type="button" class="application-select__control text-input" aria-haspopup="listbox" aria-label="Application"
        [attr.aria-expanded]="open()" [attr.aria-busy]="loading()" [disabled]="disabled()" (click)="open.set(!open())">
        {{ selectedLabel() }} <small>{{ options().length }} applications</small>
        @if(loading()) { <span class="application-select-loader" role="status" aria-label="Loading applications">…</span> }
        <span aria-hidden="true">▾</span>
      </button>
      @if(open()) {
        <div class="application-select__menu">
          <label class="form-field"><span>Find application</span>
            <input type="search" class="text-input" [value]="search()" (input)="search.set($any($event.target).value)"
              (keydown.arrowdown)="focusFirst($event)" />
          </label>
          @if(loading()) { <p role="status">Pobieranie aplikacji…</p> }
          @if(error()) { <p role="alert">{{ error() }}</p><button type="button" (click)="retry.emit()">Ponów</button> }
          <div role="listbox" aria-label="Application options">
            @for(option of filtered(); track option.systemId; let index = $index) {
              <button type="button" role="option" class="application-select__option"
                [title]="option.summary || option.systemId" [attr.aria-selected]="option.systemId === selectedId()"
                (keydown)="optionKeydown($event,index)" (click)="selected.emit(option.systemId); open.set(false)">
                {{ option.name || option.systemId }}
              </button>
            } @empty { @if(!loading()) { <p>Brak aplikacji dla aktualnego filtra.</p> } }
          </div>
        </div>
      }
    </div>`,
  styles: [`
    :host { display:block; min-width:0; } .application-select {position:relative}
    .application-select__control {width:100%; text-align:left; display:flex; justify-content:space-between}
    .application-select__menu {position:absolute; z-index:30; background:var(--surface,white);
      border:1px solid var(--border-color,#ccd5df); box-shadow:0 8px 24px #0002; padding:12px; width:100%; min-width:240px}
    [role=listbox] {max-height:280px; overflow:auto}
    .application-select__option {display:block; width:100%; text-align:left; border:0; background:transparent; padding:10px}
    .application-select__option:hover,.application-select__option:focus-visible {background:var(--surface-hover,#eef3f8)}
  `]
})
export class ApplicationSelectComponent {
  readonly options=input<ApplicationOption[]>([]);
  readonly selectedId=input('');
  readonly disabled=input(false);
  readonly loading=input(false);
  readonly error=input('');
  readonly selected=output<string>();
  readonly retry=output<void>();
  readonly open=signal(false);
  readonly search=signal('');
  readonly filtered=computed(() => {
    const q=this.search().toLocaleLowerCase().trim();
    return this.options().filter(o => [o.name,o.systemId,o.summary,...(o.aliases||[]),...(o.searchTerms||[])].join(' ').toLocaleLowerCase().includes(q));
  });
  readonly selectedLabel=computed(() => this.options().find(o=>o.systemId===this.selectedId())?.name || this.selectedId() || 'Select application');
  @HostListener('document:click') close() { this.open.set(false); }
  @HostListener('document:keydown.escape') escape() { this.open.set(false); }
  focusFirst(event: Event) {
    event.preventDefault();
    (event.target as HTMLElement).closest('.application-select__menu')?.querySelector<HTMLButtonElement>('[role=option]')?.focus();
  }
  optionKeydown(event: KeyboardEvent,index: number) {
    if(event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    event.preventDefault();
    const options = (event.target as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('[role=option]');
    options?.[Math.max(0, Math.min(options.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)))]?.focus();
  }
}

