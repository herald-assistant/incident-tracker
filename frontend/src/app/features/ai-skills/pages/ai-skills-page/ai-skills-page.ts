import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { MarkdownContentComponent } from '../../../../components/markdown-content/markdown-content';
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
  aiSkillResponsibility
} from '../../utils/ai-skills-display.utils';

type FamilyFilter = {
  id: 'all' | AiSkillFamilyId;
  label: string;
  count: number;
};

@Component({
  selector: 'app-ai-skills-page',
  imports: [ReactiveFormsModule, RouterLink, MarkdownContentComponent],
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

  constructor() {
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.searchText.set(value));
    this.responsibilityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.responsibilityFilter.set(value));
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const skillName = params.get('skillName') ?? '';
      this.selectedSkillName.set(skillName);
      this.rawView.set(false);
      this.copyMessage.set('');
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

  copySource(): void {
    const skill = this.detail();
    if (!skill) {
      return;
    }
    void copyTextToClipboard(skill.rawMarkdown).then((copied) => {
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
          }
        },
        error: (error) => {
          if (generation === this.detailRequestGeneration) {
            this.detailError.set(toErrorMessage(error, 'Nie udało się odczytać wybranego skilla.'));
          }
        }
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
