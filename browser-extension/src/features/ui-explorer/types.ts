export const UI_EXPLORER_CAPTURE_PREVIEW_VERSION =
  'tdw.ui-explorer-browser-capture-preview/v1' as const;

export interface UiElementState {
  readonly disabled: boolean;
  readonly ariaDisabled: boolean;
  readonly readOnly: boolean;
  readonly required: boolean;
  readonly invalid: boolean;
  readonly checked: boolean | null;
  readonly expanded: boolean | null;
  readonly hidden: boolean;
}

export interface UiElementDescriptor {
  readonly tag: string;
  readonly role: string | null;
  readonly accessibleName: string | null;
  readonly text: string | null;
  readonly stableAttributes: Record<string, string>;
  readonly classes: string[];
  readonly state: UiElementState;
}

export interface UiAncestorDescriptor {
  readonly depth: number;
  readonly tag: string;
  readonly role: string | null;
  readonly accessibleName: string | null;
  readonly text: string | null;
  readonly stableAttributes: Record<string, string>;
  readonly classes: string[];
}

export interface UiExplorerCapturePreview {
  readonly captureVersion: typeof UI_EXPLORER_CAPTURE_PREVIEW_VERSION;
  readonly page: {
    readonly origin: string;
    readonly pathname: string;
    readonly routeHash: string | null;
    readonly queryParameterNames: string[];
    readonly title: string;
    readonly language: string | null;
  };
  readonly selection: {
    readonly target: UiElementDescriptor;
    readonly ancestors: UiAncestorDescriptor[];
    readonly traversal: {
      readonly observedDepth: number;
      readonly emittedNodeCount: number;
      readonly omittedNodeCount: number;
      readonly reachedDocumentRoot: boolean;
    };
    readonly shadowBoundaryCount: number;
    readonly frame: 'TOP_LEVEL' | 'SAME_ORIGIN_FRAME';
    readonly viewportBounds: {
      readonly x: number;
      readonly y: number;
      readonly width: number;
      readonly height: number;
    };
  };
  readonly client: {
    readonly extensionVersion: string;
    readonly capturedAt: string;
  };
}

export interface UiExplorerDummyStartPayload {
  readonly question: string;
  readonly model: string;
  readonly reasoningEffort: string;
  readonly capture: UiExplorerCapturePreview;
}

export interface UiExplorerDummyAccepted {
  readonly jobId: string;
  readonly status: 'QUEUED';
  readonly acceptedAt: string;
  readonly mode: 'DUMMY';
}
