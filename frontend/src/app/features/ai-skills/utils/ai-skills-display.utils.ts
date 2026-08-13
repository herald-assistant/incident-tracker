export const AI_SKILL_FAMILIES = [
  { id: 'incident-analysis', label: 'Incident Analysis', prefix: 'incident-' },
  { id: 'flow-explorer', label: 'Flow Explorer', prefix: 'flow-explorer-' },
  { id: 'change-verification', label: 'Change Verification', prefix: 'change-verification-' },
  { id: 'config-drift-viewer', label: 'Config Drift Viewer', prefix: 'config-drift-viewer-' },
  { id: 'other', label: 'Other', prefix: '' }
] as const;

export type AiSkillFamilyId = (typeof AI_SKILL_FAMILIES)[number]['id'];

export function aiSkillFamily(skillName: string): (typeof AI_SKILL_FAMILIES)[number] {
  return (
    AI_SKILL_FAMILIES.find(
      (family) => family.id !== 'other' && skillName.startsWith(family.prefix)
    ) ?? AI_SKILL_FAMILIES.at(-1)!
  );
}

export function aiSkillResponsibility(skillName: string): string {
  if (skillName.endsWith('-orchestrator')) {
    return 'Orchestration';
  }
  if (skillName.includes('grounding')) {
    return 'Grounding';
  }
  if (skillName.includes('diagnostic')) {
    return 'Diagnostics';
  }
  if (skillName.includes('follow-up')) {
    return 'Follow-up';
  }
  if (skillName.includes('discovery')) {
    return 'Discovery';
  }
  if (skillName.includes('check') || skillName.includes('review')) {
    return 'Verification';
  }
  if (
    skillName.includes('section') ||
    skillName.includes('write-report') ||
    skillName.includes('functional-analysis') ||
    skillName.includes('technical-handoff')
  ) {
    return 'Result composition';
  }
  return 'Guidance';
}
