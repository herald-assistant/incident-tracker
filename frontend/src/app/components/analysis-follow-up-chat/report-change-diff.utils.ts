export interface ReportChangeDiffLine {
  kind: 'context' | 'added' | 'removed';
  text: string;
}

/** Line-based unified diff of raw Markdown. Large changes fall back to complete blocks. */
export function reportChangeDiff(before: string, after: string): ReportChangeDiffLine[] {
  const oldLines = before ? before.split(/\r?\n/) : [];
  const newLines = after ? after.split(/\r?\n/) : [];
  let prefix = 0;
  while (prefix < oldLines.length && prefix < newLines.length && oldLines[prefix] === newLines[prefix]) {
    prefix++;
  }
  let suffix = 0;
  while (suffix < oldLines.length - prefix && suffix < newLines.length - prefix &&
    oldLines[oldLines.length - 1 - suffix] === newLines[newLines.length - 1 - suffix]) {
    suffix++;
  }

  const lines: ReportChangeDiffLine[] = oldLines.slice(0, prefix).map((text) => ({ kind: 'context', text }));
  const removed = oldLines.slice(prefix, oldLines.length - suffix);
  const added = newLines.slice(prefix, newLines.length - suffix);
  if (removed.length * added.length > 200_000) {
    lines.push(...removed.map((text): ReportChangeDiffLine => ({ kind: 'removed', text })));
    lines.push(...added.map((text): ReportChangeDiffLine => ({ kind: 'added', text })));
  } else {
    const width = added.length + 1;
    const lengths = new Uint32Array((removed.length + 1) * width);
    for (let oldIndex = removed.length - 1; oldIndex >= 0; oldIndex--) {
      for (let newIndex = added.length - 1; newIndex >= 0; newIndex--) {
        const index = oldIndex * width + newIndex;
        lengths[index] = removed[oldIndex] === added[newIndex]
          ? 1 + lengths[(oldIndex + 1) * width + newIndex + 1]
          : Math.max(lengths[(oldIndex + 1) * width + newIndex], lengths[index + 1]);
      }
    }
    let oldIndex = 0;
    let newIndex = 0;
    while (oldIndex < removed.length || newIndex < added.length) {
      if (oldIndex < removed.length && newIndex < added.length && removed[oldIndex] === added[newIndex]) {
        lines.push({ kind: 'context', text: removed[oldIndex++] });
        newIndex++;
      } else if (oldIndex < removed.length &&
        (newIndex === added.length || lengths[(oldIndex + 1) * width + newIndex] >= lengths[oldIndex * width + newIndex + 1])) {
        lines.push({ kind: 'removed', text: removed[oldIndex++] });
      } else {
        lines.push({ kind: 'added', text: added[newIndex++] });
      }
    }
  }
  lines.push(...oldLines.slice(oldLines.length - suffix).map((text): ReportChangeDiffLine => ({ kind: 'context', text })));
  return lines;
}
