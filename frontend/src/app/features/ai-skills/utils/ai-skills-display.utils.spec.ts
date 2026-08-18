import {
  aiSkillFamily,
  aiSkillMarkdownBody,
  aiSkillResponsibility
} from './ai-skills-display.utils';

describe('AI Skills display projection', () => {
  it('should classify known workflow prefixes and keep a safe fallback', () => {
    expect(aiSkillFamily('incident-code-grounding').label).toBe('Incident Analysis');
    expect(aiSkillFamily('flow-explorer-orchestrator').label).toBe('Flow Explorer');
    expect(aiSkillFamily('delivery-complexity-assessment-evaluator').label).toBe(
      'Delivery Complexity Assessment'
    );
    expect(aiSkillFamily('future-skill').label).toBe('Other');
  });

  it('should classify responsibilities without changing runtime semantics', () => {
    expect(aiSkillResponsibility('flow-explorer-orchestrator')).toBe('Orchestration');
    expect(aiSkillResponsibility('incident-operational-grounding')).toBe('Grounding');
    expect(aiSkillResponsibility('change-verification-write-report')).toBe('Result composition');
    expect(aiSkillResponsibility('delivery-complexity-assessment-evaluator')).toBe(
      'Assessment'
    );
    expect(aiSkillResponsibility('future-skill')).toBe('Guidance');
  });

  it('should project editable SKILL.md source to its rendered Markdown body', () => {
    expect(
      aiSkillMarkdownBody('---\r\nname: example\r\ndescription: Example.\r\n---\r\n\r\n# Guidance')
    ).toBe('# Guidance');
    expect(aiSkillMarkdownBody('# Guidance')).toBe('# Guidance');
  });
});
