import { AssistantAuthProfile, AssistantConfiguration } from './system-assistant.models';

const AUTH_FIELDS = ['id', 'name', 'type', 'username', 'password', 'token', 'tokenUrl', 'clientId', 'clientSecret', 'scopes', 'issuerUrl', 'redirectUri', 'clientAuthentication'];

export const ASSISTANT_CONFIGURATION_FILE_NAME = 'system-assistant-configuration.json';
export const ASSISTANT_CONFIGURATION_FILE_MAX_BYTES = 10_000_000;

export interface AssistantConfigurationFile {
  version: 1;
  configuration: AssistantConfiguration;
}

export function configurationFile(configuration: AssistantConfiguration): AssistantConfigurationFile {
  return {
    version: 1,
    configuration: {
      apis: configuration.apis.map(({id, name, yaml}) => ({id, name, yaml})),
      skills: configuration.skills.map(({id, name, content}) => ({id, name, content})),
      profiles: configuration.profiles.map(({id, name, urlPattern, apiBindings, skillIds}) => ({id, name, urlPattern, apiBindings: apiBindings.map(({apiId, authProfileId}) => ({apiId, authProfileId})), skillIds: [...skillIds]})),
      authProfiles: configuration.authProfiles.map(profile => Object.fromEntries(AUTH_FIELDS.map(key => [key, profile[key as keyof AssistantAuthProfile]])) as unknown as AssistantAuthProfile)
    }
  };
}

function objectWithKeys(value: unknown, keys: string[]): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    && Object.keys(value).length === keys.length
    && keys.every(key => Object.prototype.hasOwnProperty.call(value, key));
}

export function parseConfigurationFile(value: unknown): AssistantConfiguration {
  const invalid = () => new Error('Nieprawidłowy plik konfiguracji System Assistant. Wymagany jest format JSON w wersji 1 z kontraktami, skillami, profilami i Auth.');
  if (!objectWithKeys(value, ['version', 'configuration']) || value['version'] !== 1) throw invalid();
  const configuration = value['configuration'];
  if (!objectWithKeys(configuration, ['apis', 'skills', 'profiles', 'authProfiles'])) throw invalid();
  const apis = configuration['apis'];
  const skills = configuration['skills'];
  const profiles = configuration['profiles'];
  const authProfiles = configuration['authProfiles'];
  if (!Array.isArray(apis) || !Array.isArray(skills) || !Array.isArray(profiles) || !Array.isArray(authProfiles)) throw invalid();
  if (apis.length > 12 || skills.length > 20 || profiles.length > 20 || authProfiles.length > 20) {
    throw new Error('Konfiguracja może zawierać maksymalnie 12 kontraktów, 20 skilli, 20 profili i 20 konfiguracji Auth.');
  }
  const validRows = (rows: unknown[], keys: string[]) => rows.every(row =>
    objectWithKeys(row, keys) && keys.every(key => typeof row[key] === 'string'));
  if (!validRows(apis, ['id', 'name', 'yaml']) || !validRows(skills, ['id', 'name', 'content'])) throw invalid();
  if (!authProfiles.every(profile => objectWithKeys(profile, AUTH_FIELDS)
    && ['id', 'name', 'type'].every(key => typeof profile[key] === 'string')
    && ['BASIC', 'BEARER', 'OAUTH2', 'OIDC'].includes(profile['type'] as string)
    && AUTH_FIELDS.slice(3).every(key => profile[key] === null || typeof profile[key] === 'string'))) throw invalid();
  const ids = (value: unknown, max: number) => Array.isArray(value) && value.length <= max && value.every(id => typeof id === 'string');
  if (!profiles.every(profile => objectWithKeys(profile, ['id', 'name', 'urlPattern', 'apiBindings', 'skillIds'])
    && ['id', 'name', 'urlPattern'].every(key => typeof profile[key] === 'string')
    && Array.isArray(profile['apiBindings']) && profile['apiBindings'].length <= 12 && profile['apiBindings'].every(binding =>
      objectWithKeys(binding, ['apiId', 'authProfileId']) && typeof binding['apiId'] === 'string'
      && (binding['authProfileId'] === null || typeof binding['authProfileId'] === 'string')) && ids(profile['skillIds'], 20))) throw invalid();
  return configurationFile(configuration as unknown as AssistantConfiguration).configuration;
}
