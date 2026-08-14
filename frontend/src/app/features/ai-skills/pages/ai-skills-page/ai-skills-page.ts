import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { MarkdownContentComponent } from '../../../../components/markdown-content/markdown-content';
import { SourceStatePillComponent } from '../../../../components/source-state-pill/source-state-pill';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import {
  AiSkillCatalogResponse,
  AiSkillDetailResponse,
  AiSkillSummary
} from '../../models/ai-skills.models';
import { AiSkillsApiService } from '../../services/ai-skills-api.service';
import {
  AI_SKILL_FAMILIES,
  AiSkillFamilyId,
  aiSkillFamily,
  aiSkillMarkdownBody,
  aiSkillResponsibility
} from '../../utils/ai-skills-display.utils';

type FamilyFilter = {
  id: 'all' | AiSkillFamilyId;
  label: string;
  count: number;
};

@Component({
  selector: 'app-ai-skills-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MarkdownContentComponent,
    SourceStatePillComponent
  ],
  templateUrl: './ai-skills-page.html',
  styleUrl: './ai-skills-page.scss'
})
export class AiSkillsPageComponent {
  private readonly skillsApi = inject(AiSkillsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private detailRequestGeneration = 0;

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly responsibilityControl = new FormControl('all', { nonNullable: true });
  readonly editorControl = new FormControl('', { nonNullable: true });

  readonly catalog = signal<AiSkillCatalogResponse | null>(null);
  readonly detail = signal<AiSkillDetailResponse | null>(null);
  readonly selectedSkillName = signal('');
  readonly searchText = signal('');
  readonly familyFilter = signal<'all' | AiSkillFamilyId>('all');
  readonly responsibilityFilter = signal('all');
  readonly isCatalogLoading = signal(false);
  readonly isDetailLoading = signal(false);
  readonly catalogError = signal('');
  readonly detailError = signal('');
  readonly rawView = signal(false);
  readonly copyMessage = signal('');
  readonly isEditing = signal(false);
  readonly editorPreview = signal(false);
  readonly draftRawMarkdown = signal('');
  readonly isSaving = signal(false);
  readonly isRestoring = signal(false);
  readonly mutationMessage = signal('');

  readonly skills = computed(() => this.catalog()?.skills ?? []);
  readonly familyFilters = computed<FamilyFilter[]>(() => {
    const skills = this.skills();
    const filters: FamilyFilter[] = [
      { id: 'all', label: 'All skills', count: skills.length },
      ...AI_SKILL_FAMILIES.map((family) => ({
        id: family.id,
        label: family.label,
        count: skills.filter((skill) => aiSkillFamily(skill.name).id === family.id).length
      }))
    ];
    return filters.filter((filter) => filter.id === 'all' || filter.count > 0);
  });
  readonly responsibilities = computed(() =>
    [...new Set(this.skills().map((skill) => aiSkillResponsibility(skill.name)))].sort((a, b) =>
      a.localeCompare(b)
    )
  );
  readonly filteredSkills = computed(() => {
    const query = this.searchText().trim().toLowerCase();
    const family = this.familyFilter();
    const responsibility = this.responsibilityFilter();

    return this.skills().filter((skill) => {
      const matchesQuery =
        !query ||
        skill.name.toLowerCase().includes(query) ||
        skill.description.toLowerCase().includes(query);
      const matchesFamily = family === 'all' || aiSkillFamily(skill.name).id === family;
      const matchesResponsibility =
        responsibility === 'all' || aiSkillResponsibility(skill.name) === responsibility;
      return matchesQuery && matchesFamily && matchesResponsibility;
    });
  });
  readonly resultCountLabel = computed(() => {
    const filteredCount = this.filteredSkills().length;
    const totalCount = this.skills().length;
    return filteredCount === totalCount ? `${totalCount} skills` : `${filteredCount} of ${totalCount}`;
  });
  readonly hasUnsavedChanges = computed(
    () => this.isEditing() && this.draftRawMarkdown() !== (this.detail()?.rawMarkdown ?? '')
  );
  readonly previewMarkdown = computed(() => aiSkillMarkdownBody(this.draftRawMarkdown()));

  constructor() {
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.searchText.set(value));
    this.responsibilityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.responsibilityFilter.set(value));
    this.editorControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.draftRawMarkdown.set(value));
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const skillName = params.get('skillName') ?? '';
      this.selectedSkillName.set(skillName);
      this.rawView.set(false);
      this.copyMessage.set('');
      this.isEditing.set(false);
      this.editorPreview.set(false);
      this.mutationMessage.set('');
      this.loadDetail(skillName);
    });

    this.loadCatalog();
  }

  loadCatalog(): void {
    this.isCatalogLoading.set(true);
    this.catalogError.set('');
    this.skillsApi
      .getCatalog()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isCatalogLoading.set(false))
      )
      .subscribe({
        next: (catalog) => this.catalog.set(catalog),
        error: (error) => {
          this.catalogError.set(
            toErrorMessage(error, 'Nie udało się odczytać katalogu AI Skills.')
          );
        }
      });
  }

  setFamilyFilter(family: 'all' | AiSkillFamilyId): void {
    this.familyFilter.set(family);
  }

  familyLabel(skillName: string): string {
    return aiSkillFamily(skillName).label;
  }

  responsibilityLabel(skillName: string): string {
    return aiSkillResponsibility(skillName);
  }

  toggleRawView(): void {
    this.rawView.update((value) => !value);
  }

  beginEdit(): void {
    const skill = this.detail();
    if (!skill) {
      return;
    }
    this.editorControl.setValue(skill.rawMarkdown);
    this.draftRawMarkdown.set(skill.rawMarkdown);
    this.editorPreview.set(false);
    this.mutationMessage.set('');
    this.detailError.set('');
    this.isEditing.set(true);
  }

  cancelEdit(): void {
    if (this.hasUnsavedChanges() && !window.confirm('Odrzucić niezapisane zmiany w skillu?')) {
      return;
    }
    this.resetEditor();
  }

  toggleEditorPreview(): void {
    this.editorPreview.update((value) => !value);
  }

  saveSkill(): void {
    const skill = this.detail();
    if (!skill || this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    this.detailError.set('');
    this.mutationMessage.set('');
    this.skillsApi
      .updateSkill(skill.name, { rawMarkdown: this.editorControl.value })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSaving.set(false))
      )
      .subscribe({
        next: (updated) => {
          this.applyMutation(updated);
          this.isEditing.set(false);
          this.mutationMessage.set('Skill saved.');
        },
        error: (error) => {
          this.detailError.set(toErrorMessage(error, 'Nie udało się zapisać skilla.'));
        }
      });
  }

  restoreDefault(): void {
    const skill = this.detail();
    if (!skill?.restoreAvailable || this.isRestoring()) {
      return;
    }
    if (!window.confirm(`Przywrócić domyślną treść skilla „${skill.name}”?`)) {
      return;
    }
    this.isRestoring.set(true);
    this.detailError.set('');
    this.mutationMessage.set('');
    this.skillsApi
      .restoreDefault(skill.name)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isRestoring.set(false))
      )
      .subscribe({
        next: (restored) => {
          this.applyMutation(restored);
          this.isEditing.set(false);
          this.mutationMessage.set('Default restored.');
        },
        error: (error) => {
          this.detailError.set(toErrorMessage(error, 'Nie udało się przywrócić domyślnego skilla.'));
        }
      });
  }

  canDeactivate(): boolean {
    return (
      !this.hasUnsavedChanges() || window.confirm('Opuścić widok i odrzucić niezapisane zmiany?')
    );
  }

  @HostListener('window:beforeunload', ['$event'])
  protectUnsavedChanges(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
    }
  }

  copySource(): void {
    const skill = this.detail();
    if (!skill) {
      return;
    }
    const source = this.isEditing() ? this.editorControl.value : skill.rawMarkdown;
    void copyTextToClipboard(source).then((copied) => {
      this.copyMessage.set(copied ? 'Copied' : 'Copy failed');
    });
  }

  trackSkill(_index: number, skill: AiSkillSummary): string {
    return skill.name;
  }

  private loadDetail(skillName: string): void {
    const generation = ++this.detailRequestGeneration;
    this.detail.set(null);
    this.detailError.set('');

    if (!skillName) {
      this.isDetailLoading.set(false);
      return;
    }

    this.isDetailLoading.set(true);
    this.skillsApi
      .getSkill(skillName)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (generation === this.detailRequestGeneration) {
            this.isDetailLoading.set(false);
          }
        })
      )
      .subscribe({
        next: (detail) => {
          if (generation === this.detailRequestGeneration) {
            this.detail.set(detail);
            this.editorControl.setValue(detail.rawMarkdown, { emitEvent: false });
            this.draftRawMarkdown.set(detail.rawMarkdown);
          }
        },
        error: (error) => {
          if (generation === this.detailRequestGeneration) {
            this.detailError.set(toErrorMessage(error, 'Nie udało się odczytać wybranego skilla.'));
          }
        }
      });
  }

  private resetEditor(): void {
    const rawMarkdown = this.detail()?.rawMarkdown ?? '';
    this.editorControl.setValue(rawMarkdown, { emitEvent: false });
    this.draftRawMarkdown.set(rawMarkdown);
    this.editorPreview.set(false);
    this.isEditing.set(false);
    this.detailError.set('');
  }

  private applyMutation(updated: AiSkillDetailResponse): void {
    this.detail.set(updated);
    this.editorControl.setValue(updated.rawMarkdown, { emitEvent: false });
    this.draftRawMarkdown.set(updated.rawMarkdown);
    this.editorPreview.set(false);

    this.catalog.update((catalog) => {
      if (!catalog) {
        return catalog;
      }
      const skills = catalog.skills.map((skill) =>
        skill.name === updated.name
          ? {
              name: updated.name,
              description: updated.description,
              lineCount: updated.lineCount,
              state: updated.state,
              restoreAvailable: updated.restoreAvailable
            }
          : skill
      );
      return {
        ...catalog,
        skills,
        defaultSkillCount: skills.filter((skill) => skill.state === 'DEFAULT').length,
        customSkillCount: skills.filter((skill) => skill.state === 'CUSTOM').length
      };
    });
  }
}

function toErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    const message = error.error?.message;
    if (typeof message === 'string' && message.trim()) {
      return message.trim();
    }
  }
  return fallback;
}
