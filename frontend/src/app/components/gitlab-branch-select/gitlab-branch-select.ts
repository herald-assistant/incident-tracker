import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounce, map, of, switchMap, timer } from 'rxjs';

import {
  GitLabBranchOption,
  GitLabSystemBranchesApiService,
  GitLabSystemBranchesResponse
} from '../../core/services/gitlab-system-branches-api.service';

interface BranchQuery {
  systemId: string;
  search: string;
  initial: boolean;
}

@Component({
  selector: 'app-gitlab-branch-select',
  templateUrl: './gitlab-branch-select.html',
  styleUrl: './gitlab-branch-select.scss'
})
export class GitLabBranchSelectComponent {
  readonly systemId = input('');
  readonly selectedBranch = input('');
  readonly disabled = input(false);
  readonly branchSelected = output<string>();

  private readonly api = inject(GitLabSystemBranchesApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly queries = new Subject<BranchQuery>();

  readonly open = signal(false);
  readonly filter = signal('');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly branches = signal<GitLabBranchOption[]>([]);
  readonly truncated = signal(false);
  readonly warnings = signal<string[]>([]);
  readonly selectedLabel = computed(() => this.selectedBranch().trim() || 'Select branch');

  constructor() {
    this.queries
      .pipe(
        debounce((query) => query.initial ? of(0) : timer(200)),
        switchMap((query) =>
          this.api.getBranches(query.systemId, query.search).pipe(
            map((response) => ({ query, response, error: null as HttpErrorResponse | null })),
            catchError((error: HttpErrorResponse) =>
              of({ query, response: null as GitLabSystemBranchesResponse | null, error })
            )
          )
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ query, response, error }) => {
        if (query.systemId !== this.systemId() || query.search !== this.filter().trim()) {
          return;
        }
        this.loading.set(false);
        if (error || !response) {
          this.error.set('Nie udało się pobrać gałęzi. Spróbuj ponownie.');
          this.branches.set([]);
          return;
        }
        this.error.set('');
        this.branches.set(response.branches);
        this.truncated.set(response.truncated);
        this.warnings.set(response.warnings);
        if (query.initial && !this.disabled()) {
          const current = this.selectedBranch().trim();
          const selected = response.branches.find((branch) => branch.name === current);
          const next = selected?.name ?? response.branches.find((branch) => branch.isDefault)?.name
            ?? response.branches[0]?.name ?? '';
          this.branchSelected.emit(next);
        }
      });

    effect(() => {
      const systemId = this.systemId().trim();
      untracked(() => {
        this.open.set(false);
        this.filter.set('');
        this.branches.set([]);
        this.error.set('');
        this.truncated.set(false);
        this.warnings.set([]);
        if (systemId) {
          this.search(systemId, '', true);
        } else {
          this.loading.set(false);
        }
      });
    });
  }

  @HostListener('document:click', ['$event'])
  closeOnOutsideClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.open.set(false);
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (!this.systemId() || this.disabled()) {
      return;
    }
    this.open.update((value) => !value);
  }

  changeFilter(value: string): void {
    this.filter.set(value);
    this.search(this.systemId(), value, false);
  }

  retry(): void {
    this.search(this.systemId(), this.filter(), !this.filter().trim());
  }

  select(branch: string, event: Event): void {
    event.stopPropagation();
    this.open.set(false);
    if (branch !== this.selectedBranch()) {
      this.branchSelected.emit(branch);
    }
  }

  onOptionKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusOption(index + (event.key === 'ArrowDown' ? 1 : -1));
    }
  }

  focusOption(index: number): void {
    const options = this.elementRef.nativeElement.querySelectorAll<HTMLButtonElement>('[role="option"]');
    options[Math.max(0, Math.min(index, options.length - 1))]?.focus();
  }

  private search(systemId: string, search: string, initial: boolean): void {
    if (!systemId.trim()) {
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.queries.next({ systemId: systemId.trim(), search: search.trim(), initial });
  }
}
