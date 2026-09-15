import { extensionMessage, isRecord, type ExtensionMessage } from '../../platform/message';
import {
  UI_EXPLORER_CAPTURE_PREVIEW_VERSION,
  type UiExplorerDummyStartPayload
} from './types';

export const UI_EXPLORER_START_DUMMY = 'ui-explorer:start-dummy';

export function uiExplorerDummyStartMessage(
  payload: UiExplorerDummyStartPayload
): ExtensionMessage<typeof UI_EXPLORER_START_DUMMY, UiExplorerDummyStartPayload> {
  return extensionMessage(UI_EXPLORER_START_DUMMY, payload);
}

export function isUiExplorerDummyStartPayload(value: unknown): value is UiExplorerDummyStartPayload {
  if (!isRecord(value)) {
    return false;
  }
  const capture = value['capture'];
  return (
    typeof value['question'] === 'string' &&
    value['question'].trim().length > 0 &&
    value['question'].length <= 4000 &&
    typeof value['model'] === 'string' &&
    value['model'].length <= 100 &&
    typeof value['reasoningEffort'] === 'string' &&
    value['reasoningEffort'].length <= 40 &&
    isRecord(capture) &&
    capture['captureVersion'] === UI_EXPLORER_CAPTURE_PREVIEW_VERSION &&
    isRecord(capture['page']) &&
    typeof capture['page']['origin'] === 'string' &&
    typeof capture['page']['pathname'] === 'string' &&
    isRecord(capture['selection']) &&
    isRecord(capture['selection']['target']) &&
    isRecord(capture['client']) &&
    typeof capture['client']['extensionVersion'] === 'string'
  );
}
