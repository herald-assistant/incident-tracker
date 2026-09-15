import { featureDefinition } from '../feature-definitions';
import { registerUiExplorerBackgroundHandlers } from '../features/ui-explorer/background';
import { failure, isRecord, success } from '../platform/message';
import { MessageRouter } from '../platform/background/message-router';
import {
  hasOriginPermission,
  reconcileOriginRegistrations,
  reconcileSettingsWithPermissions,
  setOriginFeatureAccess
} from '../platform/background/origin-access';
import {
  RUNTIME_GET_ORIGIN_STATE,
  RUNTIME_GET_PAGE_CONFIGURATION,
  RUNTIME_SET_ORIGIN_FEATURE,
  type OriginStateRequest,
  type OriginStateResponse,
  type PageConfigurationResponse,
  type SetOriginFeatureRequest
} from '../platform/runtime-messages';
import { loadSettings } from '../platform/settings';
import { inspectablePage } from '../platform/url';

const router = new MessageRouter();

router.register<Record<string, never>, PageConfigurationResponse>(
  RUNTIME_GET_PAGE_CONFIGURATION,
  async (_payload, sender) => {
    const page = inspectablePage(sender.tab?.url);
    if (!page) {
      return failure('UNSUPPORTED_PAGE', 'UI rozszerzenia nie jest dostepne na tej stronie.');
    }
    const settings = await loadSettings();
    return success({
      enabledFeatureIds: settings.origins[page.origin]?.enabledFeatureIds ?? []
    });
  }
);

router.register<OriginStateRequest, OriginStateResponse>(
  RUNTIME_GET_ORIGIN_STATE,
  async (payload) => {
    if (!isOriginStateRequest(payload)) {
      return failure('INVALID_ORIGIN_REQUEST', 'Nieprawidlowe zapytanie o origin.');
    }
    const settings = await loadSettings();
    return success({
      origin: payload.origin,
      enabledFeatureIds: settings.origins[payload.origin]?.enabledFeatureIds ?? [],
      permissionGranted: await hasOriginPermission(payload.origin)
    });
  }
);

router.register<SetOriginFeatureRequest, OriginStateResponse>(
  RUNTIME_SET_ORIGIN_FEATURE,
  async (payload) => {
    if (!isSetOriginFeatureRequest(payload)) {
      return failure('INVALID_FEATURE_REQUEST', 'Nieprawidlowe ustawienie funkcji originu.');
    }
    if (!featureDefinition(payload.featureId)) {
      return failure('UNKNOWN_FEATURE', 'Rozszerzenie nie zna wskazanej funkcji.');
    }

    await setOriginFeatureAccess(
      payload.origin,
      payload.featureId,
      payload.enabled,
      payload.tabId
    );
    const settings = await loadSettings();
    return success({
      origin: payload.origin,
      enabledFeatureIds: settings.origins[payload.origin]?.enabledFeatureIds ?? [],
      permissionGranted: await hasOriginPermission(payload.origin)
    });
  }
);

registerUiExplorerBackgroundHandlers(router);

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  void router.dispatch(message, sender).then(sendResponse);
  return true;
});

chrome.runtime.onInstalled.addListener(() => {
  void reconcileSettingsWithPermissions();
});

chrome.runtime.onStartup.addListener(() => {
  void reconcileOriginRegistrations();
});

chrome.permissions.onRemoved.addListener(() => {
  void reconcileSettingsWithPermissions();
});

void reconcileSettingsWithPermissions();

function isOriginStateRequest(value: unknown): value is OriginStateRequest {
  return isRecord(value) && inspectablePage(`${String(value['origin'])}/`) !== null;
}

function isSetOriginFeatureRequest(value: unknown): value is SetOriginFeatureRequest {
  if (!isRecord(value)) {
    return false;
  }
  return (
    typeof value['origin'] === 'string' &&
    inspectablePage(`${value['origin']}/`) !== null &&
    typeof value['featureId'] === 'string' &&
    typeof value['enabled'] === 'boolean' &&
    (value['tabId'] === undefined || Number.isInteger(value['tabId']))
  );
}
