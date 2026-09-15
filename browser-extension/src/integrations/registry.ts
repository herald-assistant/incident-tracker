import type { ContentFeatureModule } from '../platform/feature';

/**
 * Composition point for future site-specific modules such as a Confluence
 * context menu or a GitLab DOM enhancement. Keep the platform runtime neutral:
 * an integration owns its URL predicate, DOM lifecycle and cleanup.
 */
export const siteIntegrationRegistry: readonly ContentFeatureModule[] = [];
