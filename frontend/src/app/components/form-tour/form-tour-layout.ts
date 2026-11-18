type Size = { width: number; height: number };
type Point = { left: number; top: number };
type Bounds = Point & Size;

export interface FormTourLayout {
  readonly spotlight: Bounds | null;
  readonly panel: Point;
}

const VIEWPORT_MARGIN = 12;
const SPOTLIGHT_PADDING = 5;
const PANEL_GAP = 10;

export function formTourTargetBounds(rectangles: readonly DOMRectReadOnly[]): DOMRectReadOnly {
  const boxes = rectangles.filter(({ width, height }) => width > 0 && height > 0);
  if (boxes.length === 0) {
    return new DOMRect();
  }
  const left = Math.min(...boxes.map((box) => box.left));
  const top = Math.min(...boxes.map((box) => box.top));
  const right = Math.max(...boxes.map((box) => box.right));
  const bottom = Math.max(...boxes.map((box) => box.bottom));
  return new DOMRect(left, top, right - left, bottom - top);
}

export function positionFormTour(
  viewport: Size,
  panel: Size,
  target: DOMRectReadOnly
): FormTourLayout {
  const maxLeft = viewport.width - panel.width - VIEWPORT_MARGIN;
  const maxTop = viewport.height - panel.height - VIEWPORT_MARGIN;
  const fitPanel = (left: number, top: number): Point => ({
    left: Math.max(VIEWPORT_MARGIN, Math.min(left, maxLeft)),
    top: Math.max(VIEWPORT_MARGIN, Math.min(top, maxTop))
  });

  if (
    target.width === 0 || target.height === 0 ||
    target.right <= 0 || target.bottom <= 0 ||
    target.left >= viewport.width || target.top >= viewport.height
  ) {
    return {
      spotlight: null,
      panel: fitPanel((viewport.width - panel.width) / 2, (viewport.height - panel.height) / 2)
    };
  }

  const left = Math.max(0, target.left - SPOTLIGHT_PADDING);
  const top = Math.max(0, target.top - SPOTLIGHT_PADDING);
  const right = Math.min(viewport.width, target.right + SPOTLIGHT_PADDING);
  const bottom = Math.min(viewport.height, target.bottom + SPOTLIGHT_PADDING);
  const spotlight = { left, top, width: right - left, height: bottom - top };

  const rightOfTarget = right + PANEL_GAP;
  const leftOfTarget = left - PANEL_GAP - panel.width;
  const belowTarget = bottom + PANEL_GAP;
  const aboveTarget = top - PANEL_GAP - panel.height;

  if (rightOfTarget <= maxLeft) {
    return { spotlight, panel: fitPanel(rightOfTarget, top) };
  }
  if (leftOfTarget >= VIEWPORT_MARGIN) {
    return { spotlight, panel: fitPanel(leftOfTarget, top) };
  }
  if (belowTarget <= maxTop) {
    return { spotlight, panel: fitPanel(left, belowTarget) };
  }
  if (aboveTarget >= VIEWPORT_MARGIN) {
    return { spotlight, panel: fitPanel(left, aboveTarget) };
  }

  const moreRoomBelow = viewport.height - bottom >= top;
  return { spotlight, panel: fitPanel(left, moreRoomBelow ? maxTop : VIEWPORT_MARGIN) };
}
