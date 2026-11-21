import { configurationFile, parseConfigurationFile } from './system-assistant-configuration-file';

describe('System Assistant configuration file', () => {
  const configuration = {
    apis: [{id: 'products', name: 'Produkty żółte', yaml: 'openapi: 3.0.3\n# żółć\n'}, {id: 'orders', name: 'Orders', yaml: 'orders YAML'}],
    skills: [{id: 'first', name: 'Reguły', content: '# Instrukcje\n\n**Sprawdź** wynik.'}, {id: '', name: '', content: ''}]
  };

  it('round trips multiple contracts and skills, including incomplete drafts and empty sets', () => {
    for (const draft of [configuration, {apis: [], skills: []}]) {
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

  it.each([
    null, [], {}, {version: '1', configuration}, {version: 2, configuration},
    {version: 1, configuration, extra: true},
    {version: 1, configuration: {...configuration, profiles: []}},
    {version: 1, configuration: {apis: [], skills: null}},
    {version: 1, configuration: {apis: [{id: 'a', name: 'A'}], skills: []}},
    {version: 1, configuration: {apis: [{id: 'a', name: 'A', yaml: 1}], skills: []}},
    {version: 1, configuration: {apis: [], skills: [{id: 'a', name: 'A', content: '', extra: true}]}},
    {version: 1, configuration: {apis: Array(13).fill(configuration.apis[0]), skills: []}},
    {version: 1, configuration: {apis: [], skills: Array(21).fill(configuration.skills[0])}}
  ])('rejects unsupported versions, shapes and counts: %j', value => {
    expect(() => parseConfigurationFile(value)).toThrow();
  });
});
