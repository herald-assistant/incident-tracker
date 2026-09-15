import { describe, expect, it } from 'vitest';
import {
  DEFAULT_SETTINGS,
  normalizeSettings,
  withOriginFeature,
  withTdwBaseUrl
} from './settings';

describe('extension settings', () => {
  it('uses safe defaults for unknown persisted data', () => {
    expect(normalizeSettings({ schemaVersion: 999 })).toEqual(DEFAULT_SETTINGS);
  });

  it('adds and removes independent features on one origin without mutation', () => {
    const first = withOriginFeature(
      DEFAULT_SETTINGS,
      'https://crm.example.com',
      'ui-explorer',
      true,
      '2026-09-15T10:00:00Z'
    );
    const second = withOriginFeature(
      first,
      'https://crm.example.com',
      'confluence-menu-demo',
      true
    );
    const third = withOriginFeature(second, 'https://crm.example.com', 'ui-explorer', false);

    expect(DEFAULT_SETTINGS.origins).toEqual({});
    expect(second.origins['https://crm.example.com']?.enabledFeatureIds).toEqual([
      'confluence-menu-demo',
      'ui-explorer'
    ]);
    expect(third.origins['https://crm.example.com']?.enabledFeatureIds).toEqual([
      'confluence-menu-demo'
    ]);
  });

  it('removes an empty origin and preserves other configuration', () => {
    const configured = withTdwBaseUrl(DEFAULT_SETTINGS, 'https://tdw.example.com');
    const enabled = withOriginFeature(configured, 'https://crm.example.com', 'ui-explorer', true);
    const disabled = withOriginFeature(enabled, 'https://crm.example.com', 'ui-explorer', false);

    expect(disabled.tdwBaseUrl).toBe('https://tdw.example.com');
    expect(disabled.origins).toEqual({});
  });
});
