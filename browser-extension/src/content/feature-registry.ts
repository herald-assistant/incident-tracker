import { uiExplorerContentFeature } from '../features/ui-explorer/content-feature';
import { siteIntegrationRegistry } from '../integrations/registry';
import { assertUniqueFeatureIds, type ContentFeatureModule } from '../platform/feature';

export const contentFeatureRegistry: readonly ContentFeatureModule[] = [
  uiExplorerContentFeature,
  ...siteIntegrationRegistry
] as const;

assertUniqueFeatureIds(contentFeatureRegistry.map((module) => module.definition));
