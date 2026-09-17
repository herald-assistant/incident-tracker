import { UxInspectorViewOption } from '../models/ux-inspector.models';

export function matchCapturedRouteToView(
  capturedPath: string,
  views: UxInspectorViewOption[]
): UxInspectorViewOption | null {
  const candidates = views
    .map((view) => ({ view, score: routeMatchScore(capturedPath, view.routePattern) }))
    .filter((candidate): candidate is { view: UxInspectorViewOption; score: number } =>
      candidate.score !== null
    )
    .sort((left, right) => right.score - left.score);

  const best = candidates[0];
  if (!best || candidates[1]?.score === best.score) return null;
  return best.view;
}

function routeMatchScore(capturedPath: string, routePattern: string): number | null {
  if (!capturedPath.trim() || !routePattern.trim()) return null;
  const capturedSegments = routeSegments(capturedPath);
  const patternSegments = routeSegments(routePattern);
  return scoreSegments(capturedSegments, patternSegments, 0, 0);
}

function scoreSegments(
  captured: string[],
  pattern: string[],
  capturedIndex: number,
  patternIndex: number
): number | null {
  if (patternIndex === pattern.length) return capturedIndex === captured.length ? 0 : null;

  const patternSegment = pattern[patternIndex];
  if (patternSegment === '**') {
    let best: number | null = null;
    for (let nextCapturedIndex = capturedIndex; nextCapturedIndex <= captured.length; nextCapturedIndex++) {
      const remaining = scoreSegments(captured, pattern, nextCapturedIndex, patternIndex + 1);
      if (remaining !== null) best = Math.max(best ?? Number.NEGATIVE_INFINITY, remaining + 1);
    }
    return best;
  }

  if (capturedIndex === captured.length) return null;
  const capturedSegment = captured[capturedIndex];
  const remaining = scoreSegments(captured, pattern, capturedIndex + 1, patternIndex + 1);
  if (remaining === null) return null;

  if (patternSegment === '*') return remaining + 10;
  if (isRouteParameter(patternSegment)) return remaining + 100;
  return patternSegment === capturedSegment ? remaining + 1000 : null;
}

function routeSegments(value: string): string[] {
  const path = value.trim().split(/[?#]/, 1)[0];
  return path
    .split('/')
    .filter(Boolean)
    .map((segment) => decodeSegment(segment));
}

function decodeSegment(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch {
    return segment;
  }
}

function isRouteParameter(segment: string): boolean {
  return /^:[^/]+$/.test(segment);
}
