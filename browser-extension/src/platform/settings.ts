export const SETTINGS_STORAGE_KEY = 'tdw-browser-extension/settings-v1';
export const SETTINGS_SCHEMA_VERSION = 1 as const;

export interface OriginSettings {
  readonly origin: string;
  readonly enabledFeatureIds: string[];
  readonly grantedAt: string;
}

export interface ExtensionSettings {
  readonly schemaVersion: typeof SETTINGS_SCHEMA_VERSION;
  readonly tdwBaseUrl: string;
  readonly origins: Record<string, OriginSettings>;
}

export const DEFAULT_SETTINGS: ExtensionSettings = {
  schemaVersion: SETTINGS_SCHEMA_VERSION,
  tdwBaseUrl: 'http://localhost:8080',
  origins: {}
};

export async function loadSettings(): Promise<ExtensionSettings> {
  const stored = await chrome.storage.local.get(SETTINGS_STORAGE_KEY);
  return normalizeSettings(stored[SETTINGS_STORAGE_KEY]);
}

export async function saveSettings(settings: ExtensionSettings): Promise<void> {
  await chrome.storage.local.set({ [SETTINGS_STORAGE_KEY]: settings });
}

export function normalizeSettings(value: unknown): ExtensionSettings {
  if (!isRecord(value) || value['schemaVersion'] !== SETTINGS_SCHEMA_VERSION) {
    return cloneDefaultSettings();
  }

  const tdwBaseUrl = typeof value['tdwBaseUrl'] === 'string' ? value['tdwBaseUrl'] : '';
  const originsValue = isRecord(value['origins']) ? value['origins'] : {};
  const origins: Record<string, OriginSettings> = {};

  for (const [key, candidate] of Object.entries(originsValue)) {
    if (!isRecord(candidate) || candidate['origin'] !== key) {
      continue;
    }
    const featureIds = Array.isArray(candidate['enabledFeatureIds'])
      ? candidate['enabledFeatureIds'].filter(
          (featureId): featureId is string => typeof featureId === 'string' && featureId.length > 0
        )
      : [];
    const grantedAt =
      typeof candidate['grantedAt'] === 'string'
        ? candidate['grantedAt']
        : new Date(0).toISOString();
    origins[key] = {
      origin: key,
      enabledFeatureIds: [...new Set(featureIds)].sort(),
      grantedAt
    };
  }

  return {
    schemaVersion: SETTINGS_SCHEMA_VERSION,
    tdwBaseUrl: tdwBaseUrl || DEFAULT_SETTINGS.tdwBaseUrl,
    origins
  };
}

export function withOriginFeature(
  settings: ExtensionSettings,
  origin: string,
  featureId: string,
  enabled: boolean,
  now: string = new Date().toISOString()
): ExtensionSettings {
  const existing = settings.origins[origin];
  const enabledFeatures = new Set(existing?.enabledFeatureIds ?? []);

  if (enabled) {
    enabledFeatures.add(featureId);
  } else {
    enabledFeatures.delete(featureId);
  }

  const origins = { ...settings.origins };
  if (enabledFeatures.size === 0) {
    delete origins[origin];
  } else {
    origins[origin] = {
      origin,
      enabledFeatureIds: [...enabledFeatures].sort(),
      grantedAt: existing?.grantedAt ?? now
    };
  }

  return { ...settings, origins };
}

export function withTdwBaseUrl(settings: ExtensionSettings, tdwBaseUrl: string): ExtensionSettings {
  return { ...settings, tdwBaseUrl };
}

function cloneDefaultSettings(): ExtensionSettings {
  return { ...DEFAULT_SETTINGS, origins: {} };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
