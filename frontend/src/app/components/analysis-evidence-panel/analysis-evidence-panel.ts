import { Component, computed, effect, input, signal } from '@angular/core';

import {
  AnalysisEvidenceAttribute,
  AnalysisEvidenceSection
} from '../../core/models/analysis.models';
import {
  buildEvidenceItemKey,
  buildEvidenceSectionKey,
  formatEvidenceSectionTitle,
  isLargeAttribute
} from '../../core/utils/analysis-display.utils';
import { AttributeNamePipe } from '../../core/pipes/attribute-name.pipe';

interface EvidenceItemView {
  key: string;
  title: string;
  attributeCount: number;
  compactAttributes: AnalysisEvidenceAttribute[];
  blockAttributes: AnalysisEvidenceAttribute[];
}

interface EvidenceSectionView {
  key: string;
  title: string;
  itemCount: number;
  items: EvidenceItemView[];
}

@Component({
  selector: 'app-analysis-evidence-panel',
  imports: [AttributeNamePipe],
  templateUrl: './analysis-evidence-panel.html',
  styleUrl: './analysis-evidence-panel.scss'
})
export class AnalysisEvidencePanelComponent {
  readonly sections = input<AnalysisEvidenceSection[]>([]);

  private readonly activeSectionKey = signal<string | null>(null);
  private readonly expandedItemKeys = signal<ReadonlySet<string>>(new Set());

  protected readonly preparedSections = computed<EvidenceSectionView[]>(() =>
    this.sections().map((section, sectionIndex) => {
      const sectionKey = buildEvidenceSectionKey(section, sectionIndex);
      const items = (section.items || []).map((item, itemIndex) => {
        const itemKey = buildEvidenceItemKey(section, item, itemIndex);
        const compactAttributes = (item.attributes || []).filter(
          (attribute) => !isLargeAttribute(attribute)
        );
        const blockAttributes = (item.attributes || []).filter(isLargeAttribute);

        return {
          key: itemKey,
          title: item.title || 'Untitled item',
          attributeCount: (item.attributes || []).length,
          compactAttributes,
          blockAttributes
        };
      });

      return {
        key: sectionKey,
        title: formatEvidenceSectionTitle(section),
        itemCount: items.length,
        items
      };
    })
  );

  protected readonly activeSection = computed(() => {
    const preparedSections = this.preparedSections();
    const activeSectionKey = this.activeSectionKey();

    return (
      preparedSections.find((section) => section.key === activeSectionKey) ??
      preparedSections[0] ??
      null
    );
  });

  constructor() {
    effect(
      () => {
        const preparedSections = this.preparedSections();
        const activeSectionKey = this.activeSectionKey();
        const expandedItemKeys = this.expandedItemKeys();

        if (preparedSections.length === 0) {
          if (activeSectionKey !== null) {
            this.activeSectionKey.set(null);
          }

          if (expandedItemKeys.size > 0) {
            this.expandedItemKeys.set(new Set());
          }

          return;
        }

        const validSectionKeys = new Set(preparedSections.map((section) => section.key));
        if (!activeSectionKey || !validSectionKeys.has(activeSectionKey)) {
          this.activeSectionKey.set(preparedSections[preparedSections.length - 1].key);
        }

        const validItemKeys = new Set(
          preparedSections.flatMap((section) => section.items.map((item) => item.key))
        );
        const nextExpandedItemKeys = new Set(
          [...expandedItemKeys].filter((itemKey) => validItemKeys.has(itemKey))
        );

        if (!setsEqual(expandedItemKeys, nextExpandedItemKeys)) {
          this.expandedItemKeys.set(nextExpandedItemKeys);
        }
      },
      { allowSignalWrites: true }
    );
  }

  protected selectSection(sectionKey: string): void {
    if (this.activeSectionKey() === sectionKey) {
      return;
    }

    this.activeSectionKey.set(sectionKey);
  }

  protected onItemToggle(itemKey: string, event: Event): void {
    const detailsElement = event.currentTarget as HTMLDetailsElement | null;
    const isOpen = detailsElement?.open ?? false;

    this.expandedItemKeys.update((expandedKeys) => {
      const nextExpandedKeys = new Set(expandedKeys);
      if (isOpen) {
        nextExpandedKeys.add(itemKey);
      } else {
        nextExpandedKeys.delete(itemKey);
      }

      return nextExpandedKeys;
    });
  }

  protected isExpanded(itemKey: string): boolean {
    return this.expandedItemKeys().has(itemKey);
  }
}

function setsEqual(left: ReadonlySet<string>, right: ReadonlySet<string>): boolean {
  if (left.size !== right.size) {
    return false;
  }

  for (const value of left) {
    if (!right.has(value)) {
      return false;
    }
  }

  return true;
}
