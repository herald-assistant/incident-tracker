import type { FeatureDefinition } from '../../platform/feature';

export const UI_EXPLORER_FEATURE_ID = 'ui-explorer';

export const UI_EXPLORER_FEATURE_DEFINITION: FeatureDefinition = {
  id: UI_EXPLORER_FEATURE_ID,
  name: 'UI Explorer',
  description: 'Wskaz element strony i uruchom pytanie o jego zachowanie.',
  availability: 'all-pages'
};
