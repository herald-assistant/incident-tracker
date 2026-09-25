import { Component, computed, input, output, signal } from '@angular/core';
import { TextFieldModule } from '@angular/cdk/text-field';
import { AnalysisReport, AnalysisReportEditRequest } from '../../core/models/analysis.models';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-report-editor',
  imports: [MarkdownContentComponent, TextFieldModule],
  templateUrl: './analysis-report-editor.html',
  styleUrl: './analysis-report-editor.scss'
})
export class AnalysisReportEditorComponent {
  readonly report = input.required<AnalysisReport>();
  readonly saving = input(false);
  readonly error = input('');
  readonly save = output<AnalysisReportEditRequest>();
  readonly cancel = output<void>();
  readonly preview = signal(false);
  readonly drafts = signal<Record<string, string>>({});
  readonly parts = computed(() => [
    ...(this.report().markdownSummary ? [{ id: 'markdownSummary', title: 'Podsumowanie', markdown: this.report().markdownSummary }] : []),
    ...[...this.report().sections]
      .sort((left, right) => (left.order ?? Number.MAX_SAFE_INTEGER) - (right.order ?? Number.MAX_SAFE_INTEGER))
      .map(section => ({ id: section.id, title: section.title || section.id, markdown: section.markdown }))
  ]);
  readonly changes = computed(() => this.parts()
    .filter(part => this.drafts()[part.id] !== undefined && this.drafts()[part.id] !== part.markdown)
    .map(part => ({ id: part.id, markdown: this.drafts()[part.id] })));
  readonly canSave = computed(() => this.changes().length > 0 && this.changes().every(part => part.markdown.trim().length > 0));

  content(part: { id: string; markdown: string }): string {
    return this.drafts()[part.id] ?? part.markdown;
  }

  update(partId: string, event: Event): void {
    this.drafts.update(drafts => ({ ...drafts, [partId]: (event.target as HTMLTextAreaElement).value }));
  }

  submit(): void {
    if (!this.canSave() || this.saving() || !this.report().revisionSha256) return;
    const changes = this.changes();
    this.save.emit({
      expectedRevisionSha256: this.report().revisionSha256!,
      markdownSummary: changes.find(part => part.id === 'markdownSummary')?.markdown ?? null,
      sections: changes.filter(part => part.id !== 'markdownSummary')
        .map(part => ({ sectionId: part.id, markdown: part.markdown }))
    });
  }
}
