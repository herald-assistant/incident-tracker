import { matchesProfileUrl, missingProfileResources } from './assistant-profiles';

describe('Assistant profiles', () => {
  it.each([
    ['*', 'https://crm.example/customers/C-100', true],
    ['https://crm.example/customers/*', 'https://crm.example/customers/C-100', true],
    ['https://crm.example/customers/*', 'https://crm.example/cases/C-100', false],
    ['https://crm.example/*', 'https://other.example/customers', false],
    ['*/customers/*/notes', 'https://crm.example/customers/C-100/notes', true],
    ['*/customers/*/notes', 'https://crm.example/customers/C-100/notes/extra', false],
    ['https://crm.example/a.b', 'https://crm.example/axb', false],
    ['https://crm.example/[id]', 'https://crm.example/[id]', true],
    ['*/Customers/*', 'https://crm.example/customers/C-100', false],
    ['*a*b*', 'a---a---b---b', true], ['abc*', 'abcd', true], ['*abc', 'ab', false]
  ])('matches literal whole URL %s', (pattern, url, expected) => expect(matchesProfileUrl(pattern, url)).toBe(expected));

  it('reports missing references without removing them or their remaining resources', () => {
    const profile = {id: 'crm', name: 'CRM', urlPattern: '*', apiBindings: [{apiId: "present", authProfileId: null}, {apiId: "missing", authProfileId: null}], skillIds: ['removed']};
    const config = {authProfiles: [], profiles: [profile], apis: [{id: 'present', name: 'Present', yaml: ''}], skills: []};
    expect(missingProfileResources(profile, config)).toContain('Brak API: missing. Brak skilli: removed.');
    expect(profile.apiBindings.map(binding => binding.apiId)).toEqual(['present', 'missing']);
    expect(missingProfileResources({...profile, apiBindings: [{apiId: "present", authProfileId: null}], skillIds: []}, config)).toBe('');
  });
});
