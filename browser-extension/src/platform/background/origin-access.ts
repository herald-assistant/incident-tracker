import { runtimeRefreshMessage } from '../runtime-messages';
import { loadSettings, saveSettings, withOriginFeature } from '../settings';
import { inspectablePage, registrationIdForOrigin } from '../url';

const CONTENT_SCRIPT_PATH = 'content/content-script.js';
const REGISTRATION_PREFIX = 'tdw-origin-';

export async function hasOriginPermission(origin: string): Promise<boolean> {
  const page = inspectablePage(`${origin}/`);
  if (!page) {
    return false;
  }
  return chrome.permissions.contains({ origins: [page.permissionPattern] });
}

export async function setOriginFeatureAccess(
  origin: string,
  featureId: string,
  enabled: boolean,
  tabId?: number
): Promise<void> {
  const page = inspectablePage(`${origin}/`);
  if (!page) {
    throw new Error('Origin nie jest wspierana strona HTTP(S).');
  }

  if (enabled && !(await hasOriginPermission(origin))) {
    throw new Error('Chrome nie przyznal uprawnienia do tego originu.');
  }

  const previous = await loadSettings();
  const next = withOriginFeature(previous, origin, featureId, enabled);
  await saveSettings(next);

  if (next.origins[origin]) {
    await ensureOriginRegistration(origin);
    if (enabled) {
      await injectOrRefreshOriginTabs(page.permissionPattern, tabId);
    } else {
      await refreshOriginTabs(page.permissionPattern, tabId);
    }
    return;
  }

  await refreshOriginTabs(page.permissionPattern, tabId);
  await unregisterOrigin(origin);
  await chrome.permissions.remove({ origins: [page.permissionPattern] });
}

export async function reconcileOriginRegistrations(): Promise<void> {
  const settings = await loadSettings();
  const desiredOrigins: string[] = [];

  for (const origin of Object.keys(settings.origins)) {
    if (await hasOriginPermission(origin)) {
      desiredOrigins.push(origin);
      await ensureOriginRegistration(origin);
    }
  }

  const desiredIds = new Set(desiredOrigins.map(registrationIdForOrigin));
  const registered = await chrome.scripting.getRegisteredContentScripts();
  const staleIds = registered
    .map((script) => script.id)
    .filter((id) => id.startsWith(REGISTRATION_PREFIX) && !desiredIds.has(id));

  if (staleIds.length > 0) {
    await chrome.scripting.unregisterContentScripts({ ids: staleIds });
  }
}

export async function reconcileSettingsWithPermissions(): Promise<void> {
  const settings = await loadSettings();
  let next = settings;

  for (const [origin, originSettings] of Object.entries(settings.origins)) {
    if (await hasOriginPermission(origin)) {
      continue;
    }
    for (const featureId of originSettings.enabledFeatureIds) {
      next = withOriginFeature(next, origin, featureId, false);
    }
  }

  if (next !== settings) {
    await saveSettings(next);
  }
  await reconcileOriginRegistrations();
}

async function ensureOriginRegistration(origin: string): Promise<void> {
  const page = inspectablePage(`${origin}/`);
  if (!page) {
    return;
  }
  const id = registrationIdForOrigin(origin);
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [id] });
  const definition: chrome.scripting.RegisteredContentScript = {
    id,
    js: [CONTENT_SCRIPT_PATH],
    matches: [page.permissionPattern],
    allFrames: false,
    matchOriginAsFallback: false,
    persistAcrossSessions: true,
    runAt: 'document_start',
    world: 'ISOLATED'
  };

  if (existing.length === 0) {
    await chrome.scripting.registerContentScripts([definition]);
  } else {
    await chrome.scripting.updateContentScripts([definition]);
  }
}

async function unregisterOrigin(origin: string): Promise<void> {
  const id = registrationIdForOrigin(origin);
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [id] });
  if (existing.length > 0) {
    await chrome.scripting.unregisterContentScripts({ ids: [id] });
  }
}

async function injectOrRefreshTab(tabId: number): Promise<void> {
  try {
    await chrome.scripting.executeScript({
      target: { tabId },
      files: [CONTENT_SCRIPT_PATH],
      world: 'ISOLATED'
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!message.includes('Cannot access') && !message.includes('already')) {
      throw error;
    }
  }
  await notifyTab(tabId, runtimeRefreshMessage());
}

async function injectOrRefreshOriginTabs(
  permissionPattern: string,
  preferredTabId?: number
): Promise<void> {
  const tabIds = await matchingTabIds(permissionPattern, preferredTabId);
  for (const tabId of tabIds) {
    await injectOrRefreshTab(tabId);
  }
}

async function refreshOriginTabs(permissionPattern: string, preferredTabId?: number): Promise<void> {
  const tabIds = await matchingTabIds(permissionPattern, preferredTabId);
  for (const tabId of tabIds) {
    await notifyTab(tabId, runtimeRefreshMessage());
  }
}

async function matchingTabIds(
  permissionPattern: string,
  preferredTabId?: number
): Promise<number[]> {
  const tabs = await chrome.tabs.query({ url: [permissionPattern] });
  const tabIds = new Set<number>();
  if (preferredTabId !== undefined) {
    tabIds.add(preferredTabId);
  }
  for (const tab of tabs) {
    if (tab.id !== undefined) {
      tabIds.add(tab.id);
    }
  }
  return [...tabIds];
}

async function notifyTab(tabId: number, message: unknown): Promise<void> {
  try {
    await chrome.tabs.sendMessage(tabId, message);
  } catch {
    // The tab can navigate between permission grant and notification.
  }
}
