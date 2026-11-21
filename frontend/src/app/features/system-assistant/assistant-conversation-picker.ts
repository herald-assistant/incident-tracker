import {Component, DestroyRef, OnInit, computed, inject, input, output, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MatTooltipModule} from '@angular/material/tooltip';
import {LocalAnalysisRunListItemResponse} from '../../core/models/analysis.models';
import {AnalysisRunHistoryApiService} from '../../core/services/analysis-run-history-api.service';
import {formatDateTime} from '../../core/utils/analysis-display.utils';
import {TextFieldComponent} from '../../components/text-field/text-field';

@Component({
  selector: 'app-assistant-conversation-picker',
  imports: [TextFieldComponent, MatTooltipModule],
  templateUrl: './assistant-conversation-picker.html',
  styleUrl: './assistant-conversation-picker.scss'
})
export class AssistantConversationPicker implements OnInit {
  private readonly history = inject(AnalysisRunHistoryApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly disabled = input(false);
  readonly currentId = input('');
  readonly selectionError = input('');
  readonly selected = output<string>();
  readonly newConversation = output<void>();
  readonly cancelled = output<void>();
  readonly query = signal('');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly limit = signal(50);
  private readonly runs = signal<LocalAnalysisRunListItemResponse[]>([]);
  readonly filtered = computed(() => {
    const query = this.query().trim().toLocaleLowerCase('pl');
    return this.runs().filter(run => !query || run.name.toLocaleLowerCase('pl').includes(query));
  });
  readonly visible = computed(() => this.filtered().slice(0, this.limit()));
  readonly dateTime = formatDateTime;

  ngOnInit(): void { this.load(); }

  load(): void {
    if (this.loading() || this.disabled()) return;
    this.loading.set(true); this.error.set('');
    this.history.listRuns().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => {
        this.runs.set(result.runs.filter(run => run.feature === 'system-assistant')
          .sort((a, b) => this.timestamp(b) - this.timestamp(a)));
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.error.set('Nie udało się wczytać rozmów. Spróbuj ponownie.'); }
    });
  }

  search(value: string): void { this.query.set(value); this.limit.set(50); }
  choose(id: string): void { if (!this.disabled()) this.selected.emit(id); }
  private timestamp(run: LocalAnalysisRunListItemResponse): number { return Date.parse(run.updatedAt || run.createdAt) || 0; }
}
