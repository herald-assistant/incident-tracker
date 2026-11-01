import { Component, computed, HostListener, input, output, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface ApplicationOption {
  systemId: string; name: string | null; summary?: string | null; aliases?: string[]; searchTerms?: string[];
}
@Component({
  selector: 'app-application-select',
  imports: [MatTooltipModule],
  templateUrl: './application-select.html',
  styleUrl: './application-select.scss'
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
  readonly selectedOption=computed(() => this.options().find(o=>o.systemId===this.selectedId()));
  readonly selectedLabel=computed(() => this.selectedOption()?.name || this.selectedId() || 'Select application');
  readonly selectedDescription=computed(() => this.selectedOption()?.summary ||
    `${this.options().length} ${this.options().length === 1 ? 'application' : 'applications'}`);
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

