import { configurationFile, parseConfigurationFile } from './system-assistant-configuration-file';

describe('System Assistant configuration file', () => {
  const configuration = {authProfiles: [], profiles: [{id: 'crm', name: 'CRM', urlPattern: 'https://crm.example/*', apiBindings: [{apiId: "products", authProfileId: null}, {apiId: "removed", authProfileId: null}], skillIds: ['first']}],
    apis: [{id: 'products', name: 'Produkty żółte', yaml: 'openapi: 3.0.3\n# żółć\n'}, {id: 'orders', name: 'Orders', yaml: 'orders YAML'}],
    skills: [{id: 'first', name: 'Reguły', content: '# Instrukcje\n\n**Sprawdź** wynik.'}, {id: '', name: '', content: ''}]
  };

  it('round trips multiple contracts and skills, including incomplete drafts and empty sets', () => {
    for (const draft of [configuration, {authProfiles: [], profiles: [], apis: [], skills: []}]) {
      const restored = parseConfigurationFile(JSON.parse(JSON.stringify(configurationFile(draft))));
      expect(restored).toEqual(draft);
      expect(restored).not.toBe(draft);
      expect(restored.apis[0]).not.toBe(draft.apis[0] ?? null);
    }
  });

  it('exports only configuration fields, without runtime state or additional properties', () => {
    const draft = {...configuration, model: 'private-model', conversation: 'chat',
      apis: configuration.apis.map(api => ({...api, credentials: 'secret'})),
      skills: configuration.skills.map(skill => ({...skill, revision: 9}))};
    expect(configurationFile(draft)).toEqual({version: 1, configuration});
  });

  it('rejects the old global API authentication and profile apiIds format', () => {
    const api = configuration.apis[0];
    const profile = configuration.profiles[0];
    for (const previous of [
      {...configuration, apis: [{...api, authProfileId: null}]},
      {...configuration, profiles: [{id: profile.id, name: profile.name,
        urlPattern: profile.urlPattern, apiIds: ['products'], skillIds: []}]},
      {...configuration, profiles: [{...profile, apiBindings: [{apiId: 'products'}]}]}
    ]) {
      expect(() => parseConfigurationFile({version: 1, configuration: previous})).toThrow();
    }
  });

  it.each([
    {version: 1, configuration: {apis: [], skills: []}},
    {version: 1, configuration: {...configuration, authProfiles: [], profiles: [{id: 'crm', name: 'CRM', urlPattern: '*', apiBindings: null, skillIds: []}]}},
    {version: 1, configuration: {...configuration, authProfiles: [], profiles: Array(21).fill(configuration.profiles[0])}},
    null, [], {}, {version: '1', configuration}, {version: 2, configuration},
    {version: 1, configuration, extra: true},
    {version: 1, configuration: {...configuration, unexpected: []}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: [], skills: null}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: [{id: 'a', name: 'A'}], skills: []}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: [{id: 'a', name: 'A', yaml: 1}], skills: []}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: [], skills: [{id: 'a', name: 'A', content: '', extra: true}]}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: Array(13).fill(configuration.apis[0]), skills: []}},
    {version: 1, configuration: {authProfiles: [], profiles: [], apis: [], skills: Array(21).fill(configuration.skills[0])}}
  ])('rejects unsupported versions, shapes and counts: %j', value => {
    expect(() => parseConfigurationFile(value)).toThrow();
  });
});
