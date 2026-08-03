import { Component, ElementRef, computed, effect, inject, input, output, signal } from '@angular/core';

import {
  RuntimeConfigurationDiffAnnotation,
  RuntimeConfigurationDiffDocument,
  RuntimeConfigurationDiffFile,
  RuntimeConfigurationDiffNode,
  RuntimeConfigurationDiffProjection,
  RuntimeConfigurationDiffValue,
  RuntimeConfigurationVerificationMode
} from '../../models/runtime-configuration-verification.models';

type ConfigurationDiffView = 'CHANGES' | 'FULL';

interface ConfigurationRenderRow {
  key: string;
  node: RuntimeConfigurationDiffNode;
  depth: number;
  changed: boolean;
  descendantChanged: boolean;
  annotations: RuntimeConfigurationDiffAnnotation[];
}

interface ConfigurationRenderDocument {
  key: string;
  document: RuntimeConfigurationDiffDocument;
  rows: ConfigurationRenderRow[];
  changeCount: number;
}

interface ConfigurationRenderFile {
  key: string;
  file: RuntimeConfigurationDiffFile;
  documents: ConfigurationRenderDocument[];
  changeCount: number;
}

type InlineDiffSegmentKind = 'SAME' | 'REMOVED' | 'ADDED';

interface InlineDiffSegment {
  kind: InlineDiffSegmentKind;
  text: string;
}

interface InlineValueDiff {
  sourceText: string;
  targetText: string;
  segments: InlineDiffSegment[];
}

@Component({
  selector: 'app-runtime-configuration-diff-renderer',
  templateUrl: './runtime-configuration-diff-renderer.html',
  styleUrl: './runtime-configuration-diff-renderer.scss'
})
export class RuntimeConfigurationDiffRendererComponent {
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly projection = input.required<RuntimeConfigurationDiffProjection>();
  readonly annotations = input<RuntimeConfigurationDiffAnnotation[]>([]);
  readonly mode = input<RuntimeConfigurationVerificationMode>('BASIC');
  readonly focusedReferenceId = input('');
  readonly referenceSelected = output<string>();

  readonly view = signal<ConfigurationDiffView>('CHANGES');

  private readonly annotationsByDifferenceId = computed(() => {
    const index = new Map<string, RuntimeConfigurationDiffAnnotation[]>();
    for (const annotation of this.annotations()) {
      for (const differenceId of annotation.differenceIds ?? []) {
        const current = index.get(differenceId) ?? [];
        if (!current.some((candidate) => candidate.sourceId === annotation.sourceId)) {
          index.set(differenceId, [...current, annotation]);
        }
      }
    }
    return index;
  });

  readonly files = computed<ConfigurationRenderFile[]>(() => {
    const projection = this.projection();
    return projection.files
      .map((file, fileIndex) => this.renderFile(file, fileIndex))
      .filter((file) => this.view() === 'FULL' || file.changeCount > 0)
      .sort((left, right) => this.fileOrder(left.file) - this.fileOrder(right.file));
  });

  readonly totalChanges = computed(() =>
    this.projection().files.reduce((total, file) => total + this.changeCount(file), 0)
  );

  constructor() {
    effect(() => {
      const referenceId = this.focusedReferenceId();
      if (!referenceId) {
        return;
      }
      window.setTimeout(() => {
        const target = Array.from(
          this.elementRef.nativeElement.querySelectorAll<HTMLElement>('[data-difference-id]')
        ).find((element) => element.dataset['differenceId'] === referenceId);
        if (!target) {
          return;
        }
        let parent = target.parentElement;
        while (parent) {
          if (parent instanceof HTMLDetailsElement) {
            parent.open = true;
          }
          parent = parent.parentElement;
        }
        if (typeof target.scrollIntoView === 'function') {
          target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      });
    });
  }

  protected selectView(view: ConfigurationDiffView): void {
    this.view.set(view);
  }

  protected isFocused(row: ConfigurationRenderRow): boolean {
    return row.node.differenceIds.includes(this.focusedReferenceId());
  }

  protected markerClass(kind: string): string {
    return ['ADDED', 'REMOVED'].includes(kind)
      ? 'change-marker--critical'
      : ['CHANGED', 'TYPE_CHANGED'].includes(kind)
        ? 'change-marker--changed'
        : kind === 'EFFECTIVE_CHANGED'
          ? 'change-marker--effective'
          : '';
  }

  protected marker(kind: string): string {
    return ['ADDED', 'REMOVED'].includes(kind)
      ? '🔴'
      : ['CHANGED', 'TYPE_CHANGED'].includes(kind)
        ? '🟠'
        : kind === 'EFFECTIVE_CHANGED'
          ? '🟡'
          : '';
  }

  protected changeLabel(kind: string): string {
    const labels: Record<string, string> = {
      ADDED: 'dodano',
      REMOVED: 'usunięto',
      CHANGED: 'zmieniono',
      TYPE_CHANGED: 'zmiana typu',
      EFFECTIVE_CHANGED: 'zmiana efektywna',
      UNCHANGED: 'bez zmian'
    };
    return labels[kind] ?? kind.toLowerCase().replaceAll('_', ' ');
  }

  protected fileLabel(file: RuntimeConfigurationDiffFile): string {
    return file.targetPath ?? file.sourcePath ?? this.changeLabel(file.role);
  }

  protected roleLabel(role: string): string {
    return role.toLowerCase().replaceAll('_', ' ');
  }

  protected formatValue(value: RuntimeConfigurationDiffValue): string {
    if (value.presence === 'ABSENT') {
      return 'BRAK';
    }
    if (value.type === 'MAP') {
      return '{}';
    }
    if (value.type === 'LIST') {
      return '[]';
    }
    if (value.type === 'NULL') {
      return 'null';
    }
    if (value.type === 'STRING') {
      return JSON.stringify(value.value ?? '');
    }
    if (typeof value.value === 'undefined') {
      return 'null';
    }
    return typeof value.value === 'object'
      ? JSON.stringify(value.value)
      : String(value.value);
  }

  protected sameSides(node: RuntimeConfigurationDiffNode): boolean {
    return this.sameValue(node.source, node.target);
  }

  protected presenceDiff(node: RuntimeConfigurationDiffNode): boolean {
    return node.source.presence !== node.target.presence;
  }

  protected inlineValueDiff(node: RuntimeConfigurationDiffNode): InlineValueDiff | null {
    if (
      node.source.presence !== 'PRESENT'
      || node.target.presence !== 'PRESENT'
      || this.sameSides(node)
    ) {
      return null;
    }
    return this.buildInlineValueDiff(node.source, node.target);
  }

  protected effectiveValueDiff(node: RuntimeConfigurationDiffNode): InlineValueDiff | null {
    if (
      node.changeKind !== 'EFFECTIVE_CHANGED'
      || node.sourceEffective?.presence !== 'PRESENT'
      || node.targetEffective?.presence !== 'PRESENT'
    ) {
      return null;
    }
    return this.buildInlineValueDiff(node.sourceEffective, node.targetEffective);
  }

  private buildInlineValueDiff(
    source: RuntimeConfigurationDiffValue,
    target: RuntimeConfigurationDiffValue
  ): InlineValueDiff {
    const sourceText = this.formatValue(source);
    const targetText = this.formatValue(target);
    if (source.type !== 'STRING' || target.type !== 'STRING') {
      return {
        sourceText,
        targetText,
        segments: [
          { kind: 'REMOVED', text: sourceText },
          { kind: 'ADDED', text: targetText }
        ]
      };
    }

    let prefixLength = 0;
    const sharedLength = Math.min(sourceText.length, targetText.length);
    while (
      prefixLength < sharedLength
      && sourceText[prefixLength] === targetText[prefixLength]
    ) {
      prefixLength++;
    }

    let suffixLength = 0;
    while (
      suffixLength < sharedLength - prefixLength
      && sourceText[sourceText.length - 1 - suffixLength]
        === targetText[targetText.length - 1 - suffixLength]
    ) {
      suffixLength++;
    }

    const sourceDifferenceEnd = sourceText.length - suffixLength;
    const targetDifferenceEnd = targetText.length - suffixLength;
    const segments: InlineDiffSegment[] = [];
    this.appendInlineSegment(segments, 'SAME', sourceText.slice(0, prefixLength));
    this.appendInlineSegment(
      segments,
      'REMOVED',
      sourceText.slice(prefixLength, sourceDifferenceEnd)
    );
    this.appendInlineSegment(
      segments,
      'ADDED',
      targetText.slice(prefixLength, targetDifferenceEnd)
    );
    this.appendInlineSegment(segments, 'SAME', sourceText.slice(sourceDifferenceEnd));

    return { sourceText, targetText, segments };
  }

  protected leaf(node: RuntimeConfigurationDiffNode): boolean {
    return node.children.length === 0;
  }

  protected syntaxName(
    file: RuntimeConfigurationDiffFile,
    row: ConfigurationRenderRow
  ): string {
    const listItem = /^\[\d+]$/.test(row.node.name);
    if (file.format === 'YAML') {
      return listItem ? '-' : `${row.node.name}:`;
    }
    if (listItem) {
      return '-';
    }
    return this.collection(row.node) ? `${row.node.name} {` : `${row.node.name} =`;
  }

  protected collection(node: RuntimeConfigurationDiffNode): boolean {
    return ['MAP', 'LIST'].includes(node.source.type ?? '')
      || ['MAP', 'LIST'].includes(node.target.type ?? '');
  }

  protected chooseAnnotation(annotation: RuntimeConfigurationDiffAnnotation): void {
    this.referenceSelected.emit(annotation.sourceId);
  }

  protected annotationLabel(annotation: RuntimeConfigurationDiffAnnotation): string {
    return annotation.kind === 'FUNCTIONAL_IMPACT' ? 'Wpływ funkcjonalny' : 'Obserwacja AI';
  }

  private fileOrder(file: RuntimeConfigurationDiffFile): number {
    const order: Record<string, number> = {
      APPLICATION_YAML: 0,
      LOCAL_VAR: 1,
      GLOBAL_VAR: 2
    };
    return order[file.role] ?? 3;
  }

  private renderFile(file: RuntimeConfigurationDiffFile, fileIndex: number): ConfigurationRenderFile {
    const key = `${file.role}:${file.sourcePath ?? ''}:${file.targetPath ?? ''}:${fileIndex}`;
    return {
      key,
      file,
      changeCount: this.changeCount(file),
      documents: file.documents.map((document) => ({
        key: `${key}:document:${document.documentIndex}`,
        document,
        changeCount: this.nodeChangeCount(document.root),
        rows: this.renderRows(file, document, key)
      })).filter((document) => this.view() === 'FULL' || document.changeCount > 0)
    };
  }

  private renderRows(
    file: RuntimeConfigurationDiffFile,
    document: RuntimeConfigurationDiffDocument,
    fileKey: string
  ): ConfigurationRenderRow[] {
    const rows: ConfigurationRenderRow[] = [];
    const root = document.root;
    if (!root.path && root.children.length) {
      root.children.forEach((node) =>
        this.appendNode(rows, file, document, node, 0, fileKey)
      );
    } else {
      this.appendNode(rows, file, document, root, 0, fileKey);
    }
    return rows;
  }

  private appendNode(
    rows: ConfigurationRenderRow[],
    file: RuntimeConfigurationDiffFile,
    document: RuntimeConfigurationDiffDocument,
    node: RuntimeConfigurationDiffNode,
    depth: number,
    fileKey: string
  ): void {
    const changed = node.changeKind !== 'UNCHANGED';
    const descendantChanged = node.children.some((child) => this.subtreeChanged(child));
    if (this.view() === 'CHANGES' && !changed && !descendantChanged) {
      return;
    }

    const key = `${fileKey}:${document.documentIndex}:${node.path || node.name}`;
    const row: ConfigurationRenderRow = {
      key,
      node,
      depth,
      changed,
      descendantChanged,
      annotations: this.annotationsFor(node)
    };
    rows.push(row);

    node.children.forEach((child) =>
      this.appendNode(rows, file, document, child, depth + 1, fileKey)
    );
  }

  private annotationsFor(node: RuntimeConfigurationDiffNode): RuntimeConfigurationDiffAnnotation[] {
    if (this.mode() !== 'DEEP') {
      return [];
    }
    const result: RuntimeConfigurationDiffAnnotation[] = [];
    for (const differenceId of node.differenceIds) {
      for (const annotation of this.annotationsByDifferenceId().get(differenceId) ?? []) {
        if (!result.some((candidate) => candidate.sourceId === annotation.sourceId)) {
          result.push(annotation);
        }
      }
    }
    return result;
  }

  private changeCount(file: RuntimeConfigurationDiffFile): number {
    return file.documents.reduce(
      (total, document) => total + this.nodeChangeCount(document.root),
      0
    );
  }

  private nodeChangeCount(node: RuntimeConfigurationDiffNode): number {
    return node.differenceIds.length
      + node.children.reduce((total, child) => total + this.nodeChangeCount(child), 0);
  }

  private subtreeChanged(node: RuntimeConfigurationDiffNode): boolean {
    return node.changeKind !== 'UNCHANGED'
      || node.children.some((child) => this.subtreeChanged(child));
  }

  private sameValue(
    source: RuntimeConfigurationDiffValue,
    target: RuntimeConfigurationDiffValue
  ): boolean {
    return source.presence === target.presence
      && source.type === target.type
      && source.cardinality === target.cardinality
      && JSON.stringify(source.value) === JSON.stringify(target.value);
  }

  private appendInlineSegment(
    segments: InlineDiffSegment[],
    kind: InlineDiffSegmentKind,
    text: string
  ): void {
    if (text) {
      segments.push({ kind, text });
    }
  }
}
