export interface FeatureDefinition {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly availability: 'all-pages' | 'site-specific';
}

export interface PageContext {
  readonly url: string;
  readonly origin: string;
  readonly pathname: string;
  readonly title: string;
  readonly topFrame: boolean;
}

export interface ContentFeatureContext {
  readonly page: PageContext;
  readonly extensionVersion: string;
}

export interface MountedContentFeature {
  dispose(): void;
}

export interface ContentFeatureModule {
  readonly definition: FeatureDefinition;
  matches(page: PageContext): boolean;
  mount(context: ContentFeatureContext): MountedContentFeature;
}

export function assertUniqueFeatureIds(definitions: readonly FeatureDefinition[]): void {
  const ids = new Set<string>();
  for (const definition of definitions) {
    if (!definition.id.trim()) {
      throw new Error('Feature id cannot be empty.');
    }
    if (ids.has(definition.id)) {
      throw new Error(`Duplicate feature id: ${definition.id}`);
    }
    ids.add(definition.id);
  }
}
