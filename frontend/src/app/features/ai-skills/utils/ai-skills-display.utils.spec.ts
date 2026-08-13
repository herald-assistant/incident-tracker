import { aiSkillFamily, aiSkillResponsibility } from './ai-skills-display.utils';

describe('AI Skills display projection', () => {
  it('should classify known workflow prefixes and keep a safe fallback', () => {
    expect(aiSkillFamily('incident-code-grounding').label).toBe('Incident Analysis');
    expect(aiSkillFamily('flow-explorer-orchestrator').label).toBe('Flow Explorer');
    expect(aiSkillFamily('future-skill').label).toBe('Other');
  });

  it('should classify responsibilities without changing runtime semantics', () => {
    expect(aiSkillResponsibility('flow-explorer-orchestrator')).toBe('Orchestration');
    expect(aiSkillResponsibility('incident-operational-grounding')).toBe('Grounding');
    expect(aiSkillResponsibility('change-verification-write-report')).toBe('Result composition');
    expect(aiSkillResponsibility('future-skill')).toBe('Guidance');
  });
});
