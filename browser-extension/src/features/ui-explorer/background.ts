import { failure, success } from '../../platform/message';
import type { MessageRouter } from '../../platform/background/message-router';
import { inspectablePage } from '../../platform/url';
import { UI_EXPLORER_START_DUMMY, isUiExplorerDummyStartPayload } from './messages';
import type { UiExplorerDummyAccepted, UiExplorerDummyStartPayload } from './types';

export function registerUiExplorerBackgroundHandlers(router: MessageRouter): void {
  router.register<UiExplorerDummyStartPayload, UiExplorerDummyAccepted>(
    UI_EXPLORER_START_DUMMY,
    async (payload, sender) => {
      if (!isUiExplorerDummyStartPayload(payload)) {
        return failure('INVALID_UI_EXPLORER_DUMMY_REQUEST', 'Nieprawidlowe dane modala demo.');
      }
      const senderPage = inspectablePage(sender.tab?.url);
      if (!senderPage || senderPage.origin !== payload.capture.page.origin) {
        return failure(
          'UI_EXPLORER_PAGE_MISMATCH',
          'Origin capture nie odpowiada karcie, z ktorej wyslano zadanie.'
        );
      }

      await delay(320);
      return success({
        jobId: `demo-${crypto.randomUUID()}`,
        status: 'QUEUED',
        acceptedAt: new Date().toISOString(),
        mode: 'DUMMY'
      });
    }
  );
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}
