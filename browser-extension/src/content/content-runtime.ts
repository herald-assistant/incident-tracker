import { contentFeatureRegistry } from './feature-registry';
import type { MountedContentFeature, PageContext } from '../platform/feature';
import { isExtensionMessage, sendExtensionMessage } from '../platform/message';
import {
  RUNTIME_DISPOSE,
  RUNTIME_REFRESH,
  pageConfigurationMessage,
  type PageConfigurationResponse
} from '../platform/runtime-messages';

export class ContentRuntime {
  private readonly mountedFeatures = new Map<string, MountedContentFeature>();
  private readonly extensionVersion = chrome.runtime.getManifest().version;
  private disposed = false;

  async start(): Promise<void> {
    chrome.runtime.onMessage.addListener(this.onRuntimeMessage);
    await this.refresh();
  }

  async refresh(): Promise<void> {
    if (this.disposed) {
      return;
    }
    const response = await sendExtensionMessage<PageConfigurationResponse>(
      pageConfigurationMessage()
    );
    if (!response.ok) {
      this.disposeMountedFeatures();
      return;
    }

    const page = currentPageContext();
    const enabled = new Set(response.data.enabledFeatureIds);
    for (const [featureId, mounted] of this.mountedFeatures) {
      const module = contentFeatureRegistry.find((candidate) => candidate.definition.id === featureId);
      if (!enabled.has(featureId) || !module?.matches(page)) {
        mounted.dispose();
        this.mountedFeatures.delete(featureId);
      }
    }

    for (const module of contentFeatureRegistry) {
      const featureId = module.definition.id;
      if (!enabled.has(featureId) || this.mountedFeatures.has(featureId) || !module.matches(page)) {
        continue;
      }
      this.mountedFeatures.set(
        featureId,
        module.mount({ page, extensionVersion: this.extensionVersion })
      );
    }
  }

  dispose(): void {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    chrome.runtime.onMessage.removeListener(this.onRuntimeMessage);
    this.disposeMountedFeatures();
  }

  private readonly onRuntimeMessage = (message: unknown): undefined => {
    if (!isExtensionMessage(message)) {
      return undefined;
    }
    if (message.type === RUNTIME_REFRESH) {
      void this.refresh();
    } else if (message.type === RUNTIME_DISPOSE) {
      this.dispose();
    }
    return undefined;
  };

  private disposeMountedFeatures(): void {
    for (const mounted of this.mountedFeatures.values()) {
      mounted.dispose();
    }
    this.mountedFeatures.clear();
  }
}

function currentPageContext(): PageContext {
  return {
    url: window.location.href,
    origin: window.location.origin,
    pathname: window.location.pathname,
    title: document.title,
    topFrame: window.top === window
  };
}
