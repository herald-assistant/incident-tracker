import type { ContentFeatureModule } from '../../platform/feature';
import { UiExplorerController } from './controller';
import { UI_EXPLORER_FEATURE_DEFINITION } from './definition';

export const uiExplorerContentFeature: ContentFeatureModule = {
  definition: UI_EXPLORER_FEATURE_DEFINITION,
  matches: (page) =>
    page.topFrame && (page.url.startsWith('https://') || page.url.startsWith('http://')),
  mount: (context) => new UiExplorerController(context.extensionVersion)
};
