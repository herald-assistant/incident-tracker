import { extensionMessage, type ExtensionMessage } from './message';

export const RUNTIME_GET_PAGE_CONFIGURATION = 'runtime:get-page-configuration';
export const RUNTIME_GET_ORIGIN_STATE = 'runtime:get-origin-state';
export const RUNTIME_SET_ORIGIN_FEATURE = 'runtime:set-origin-feature';
export const RUNTIME_REFRESH = 'runtime:refresh';
export const RUNTIME_DISPOSE = 'runtime:dispose';

export interface PageConfigurationResponse {
  readonly enabledFeatureIds: string[];
}

export interface OriginStateRequest {
  readonly origin: string;
}

export interface OriginStateResponse {
  readonly origin: string;
  readonly enabledFeatureIds: string[];
  readonly permissionGranted: boolean;
}

export interface SetOriginFeatureRequest {
  readonly origin: string;
  readonly featureId: string;
  readonly enabled: boolean;
  readonly tabId?: number;
}

export function pageConfigurationMessage(): ExtensionMessage<
  typeof RUNTIME_GET_PAGE_CONFIGURATION,
  Record<string, never>
> {
  return extensionMessage(RUNTIME_GET_PAGE_CONFIGURATION, {});
}

export function originStateMessage(
  origin: string
): ExtensionMessage<typeof RUNTIME_GET_ORIGIN_STATE, OriginStateRequest> {
  return extensionMessage(RUNTIME_GET_ORIGIN_STATE, { origin });
}

export function setOriginFeatureMessage(
  payload: SetOriginFeatureRequest
): ExtensionMessage<typeof RUNTIME_SET_ORIGIN_FEATURE, SetOriginFeatureRequest> {
  return extensionMessage(RUNTIME_SET_ORIGIN_FEATURE, payload);
}

export function runtimeRefreshMessage(): ExtensionMessage<
  typeof RUNTIME_REFRESH,
  Record<string, never>
> {
  return extensionMessage(RUNTIME_REFRESH, {});
}

export function runtimeDisposeMessage(): ExtensionMessage<
  typeof RUNTIME_DISPOSE,
  Record<string, never>
> {
  return extensionMessage(RUNTIME_DISPOSE, {});
}
