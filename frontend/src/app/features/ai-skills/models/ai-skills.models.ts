export type AiSkillSummary = {
  name: string;
  description: string;
  lineCount: number;
};

export type AiSkillCatalogResponse = {
  contract: 'ai-skills.catalog';
  version: number;
  mode: 'READ_ONLY';
  source: 'COPILOT_RUNTIME';
  skillCount: number;
  skills: AiSkillSummary[];
};

export type AiSkillDetailResponse = {
  contract: 'ai-skills.detail';
  version: number;
  mode: 'READ_ONLY';
  source: 'COPILOT_RUNTIME';
  name: string;
  description: string;
  lineCount: number;
  markdown: string;
  rawMarkdown: string;
};
