import { describe, expect, it, vi } from 'vitest';
import { isUiExplorerActivationChord } from './modifier';

describe('UI Explorer activation chord', () => {
  it('activates only for Ctrl and Alt together', () => {
    expect(
      isUiExplorerActivationChord({
        ctrlKey: true,
        altKey: true,
        getModifierState: vi.fn(() => false)
      })
    ).toBe(true);
    expect(
      isUiExplorerActivationChord({
        ctrlKey: true,
        altKey: false,
        getModifierState: vi.fn(() => false)
      })
    ).toBe(false);
  });

  it('does not treat AltGr as activation', () => {
    expect(
      isUiExplorerActivationChord({
        ctrlKey: true,
        altKey: true,
        getModifierState: vi.fn((key) => key === 'AltGraph')
      })
    ).toBe(false);
  });
});
