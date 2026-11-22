import { AssistantConfiguration, AssistantProfile } from './system-assistant.models';

/** Whole URL glob: only * is special; the remaining characters are literal. */
export function matchesProfileUrl(pattern: string, url: string): boolean {
  let p = 0, u = 0, star = -1, retry = 0;
  while (u < url.length) {
    if (pattern[p] === '*') { star = p++; retry = u; }
    else if (p < pattern.length && pattern[p] === url[u]) { p++; u++; }
    else if (star >= 0) { p = star + 1; u = ++retry; }
    else return false;
  }
  while (pattern[p] === '*') p++;
  return p === pattern.length;
}

export function missingProfileResources(profile: AssistantProfile, configuration: AssistantConfiguration): string {
  const missingApis = profile.apiBindings.map(binding => binding.apiId).filter(id => !configuration.apis.some(api => api.id === id));
  const missingSkills = profile.skillIds.filter(id => !configuration.skills.some(skill => skill.id === id));
  const parts = [missingApis.length ? `Brak API: ${missingApis.join(', ')}.` : '',
    missingSkills.length ? `Brak skilli: ${missingSkills.join(', ')}.` : ''].filter(Boolean);
  return parts.length ? parts.join(' ') + ' Profil używa pozostałych przypisanych zasobów.' : '';
}
