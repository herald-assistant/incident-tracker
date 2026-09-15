export interface ModifierStateLike {
  readonly ctrlKey: boolean;
  readonly altKey: boolean;
  getModifierState(key: string): boolean;
}

export function isUiExplorerActivationChord(event: ModifierStateLike): boolean {
  return event.ctrlKey && event.altKey && !event.getModifierState('AltGraph');
}
