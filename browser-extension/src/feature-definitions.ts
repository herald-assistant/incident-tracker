import { UI_EXPLORER_FEATURE_DEFINITION } from './features/ui-explorer/definition';
import { assertUniqueFeatureIds, type FeatureDefinition } from './platform/feature';

export const FEATURE_DEFINITIONS: readonly FeatureDefinition[] = [
  UI_EXPLORER_FEATURE_DEFINITION
] as const;

assertUniqueFeatureIds(FEATURE_DEFINITIONS);

export function featureDefinition(featureId: string): FeatureDefinition | undefined {
  return FEATURE_DEFINITIONS.find((definition) => definition.id === featureId);
}
