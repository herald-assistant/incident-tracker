import { describe, expect, it } from 'vitest';
import { FEATURE_DEFINITIONS } from '../feature-definitions';
import { contentFeatureRegistry } from '../content/feature-registry';
import { assertUniqueFeatureIds } from './feature';

describe('feature registry', () => {
  it('contains one definition for every registered content feature', () => {
    expect(contentFeatureRegistry.map((module) => module.definition.id)).toEqual(
      FEATURE_DEFINITIONS.map((definition) => definition.id)
    );
  });

  it('rejects duplicate ids', () => {
    expect(() =>
      assertUniqueFeatureIds([
        { id: 'crm-action', name: 'One', description: '', availability: 'all-pages' },
        { id: 'crm-action', name: 'Two', description: '', availability: 'site-specific' }
      ])
    ).toThrow('Duplicate feature id: crm-action');
  });
});
