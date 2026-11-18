import { formTourTargetBounds, positionFormTour } from './form-tour-layout';

it('includes both rendered controls in a form group without its own layout box', () => {
  const bounds = formTourTargetBounds([
    new DOMRect(16, 120, 288, 58),
    new DOMRect(16, 190, 288, 58)
  ]);

  expect([bounds.left, bounds.top, bounds.width, bounds.height]).toEqual([16, 120, 288, 128]);
});

describe('positionFormTour', () => {
  it('keeps the panel within a narrow viewport and places it above a lower control', () => {
    const viewport = { width: 320, height: 640 };
    const panel = { width: 296, height: 230 };

    const layout = positionFormTour(viewport, panel, new DOMRect(16, 500, 288, 60));

    expect(layout.spotlight).toEqual({ left: 11, top: 495, width: 298, height: 70 });
    expect(layout.panel.left).toBe(12);
    expect(layout.panel.top + panel.height).toBeLessThan(layout.spotlight!.top);
  });

  it('fits to the viewport when a large control leaves no room around it', () => {
    const layout = positionFormTour(
      { width: 320, height: 480 },
      { width: 296, height: 220 },
      new DOMRect(-20, -10, 370, 500)
    );

    expect(layout.spotlight).toEqual({ left: 0, top: 0, width: 320, height: 480 });
    expect(layout.panel).toEqual({ left: 12, top: 248 });
  });
});
